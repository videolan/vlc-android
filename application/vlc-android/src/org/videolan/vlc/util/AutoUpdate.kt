/*
 * ************************************************************************
 *  AutoUpdate.kt
 * *************************************************************************
 * Copyright © 2024 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.videolan.tools.KEY_LAST_UPDATE_TIME
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.getUpdateUri
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


object AutoUpdate {
    private const val TAG = "AutoUpdate"

    /**
     * Checks if an update is available in the nightlies
     *
     * @param context [Application] used to get the settings
     * @param skipChecks If true, the checks are skipped
     * @param listener Function called when an update is found
     */
    suspend fun checkUpdate(context: Application, skipChecks:Boolean = false, listener: (String, Date) -> Unit) = withContext(Dispatchers.IO) {
        //limit to debug builds (nightlies are included)
        if (!BuildConfig.DEBUG && !skipChecks) return@withContext

        //check if last update is older than 6 hours

        val settings = Settings.getInstance(context)
        if (!skipChecks && settings.getLong(KEY_LAST_UPDATE_TIME, 0L) > System.currentTimeMillis() - 6 * 3600000) {
            Log.i(TAG, "Last update is less than 6 hours")
            return@withContext
        }
        settings.putSingle(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())

        try {
            val arch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS[0]
            } else {
                Build.CPU_ABI
            }

            //get this abi:

            val abiCodes = mapOf(Pair("armeabi-v7a", "armv7"), Pair("arm64-v8a", "arm64"), Pair("x86", "x86"), Pair("x86_64", "x86_64"))
            if (!abiCodes.containsKey(arch)) throw Exception("Unsupported architecture")
            val abi = abiCodes[arch]
            Log.i(TAG, "Checking for update for abi! $abi")


            val buildTime = if (skipChecks) "2000-01-01" else context.getString(R.string.build_time)

            val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val webFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
            val buildDate = localFormat.parse(buildTime)
            val url = URL("http://artifacts.videolan.org/vlc-android/nightly-$abi/")
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).build()


            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext
                val m = Patterns.WEB_URL.matcher(body)
                var found = false
                while (m.find() && !found) {
                    val decodedUrl = m.group()
                    val splitUrl = decodedUrl.split('-')
                    try {
                        val nightlyDate = webFormat.parse(splitUrl[splitUrl.size - 2])
                        nightlyDate?.let {
                            if (nightlyDate.time > (buildDate?.time ?: Long.MAX_VALUE)) {
                                Log.i(TAG, "Found update: $decodedUrl")
                                withContext(Dispatchers.Main) {
                                    listener.invoke("http://artifacts.videolan.org/vlc-android/nightly-$abi/$decodedUrl", nightlyDate)
                                }
                                found = true

                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Downloads the update and installs it
     *
     * @param context [Application] used for downloading and installing the update
     * @param updateURL URL of the update
     * @param loading Function called when the update is downloading
     */
    suspend fun downloadAndInstall(context: Application, updateURL: String, loading: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { loading.invoke(true) }
        download(context, updateURL)
        withContext(Dispatchers.Main) { loading.invoke(false) }
        installAPK(context)
    }

    /**
     * Downloads the update
     *
     * @param context [Application] used for downloading the update
     * @param url URL of the update
     */
    @Throws(IOException::class)
    private fun download(context: Application, url: String) {
        val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        val downloadedFile = File(context.getCacheDir(), "update.apk")
        val sink: BufferedSink = downloadedFile.sink().buffer()
        sink.writeAll(response.body!!.source())
        sink.close()
    }

    /**
     * Installs the update
     *
     * @param context [Application] used for installing the update
     */
    private fun installAPK(context: Application) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(getUpdateUri(), "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, e.message, e)
        }
    }

    suspend fun clean(context: Application) = withContext(Dispatchers.IO) {
        try {
            val downloadedFile = File(context.getCacheDir(), "update.apk")
            if (downloadedFile.exists()) downloadedFile.delete() else { }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }


}