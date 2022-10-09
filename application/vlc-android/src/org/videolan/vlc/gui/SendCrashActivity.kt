/*
 * *************************************************************************
 *  FilePickerActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.resources.CRASH_ML_CTX
import org.videolan.resources.CRASH_ML_MSG
import org.videolan.tools.AppUtils
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.DebugLogService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SendCrashActivityBinding
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import java.io.File
import java.io.IOException

class SendCrashActivity : AppCompatActivity(), DebugLogService.Client.Callback {
    private var logMessage = ""
    override fun onStarted(logList: List<String>) {
        logMessage = "Starting collecting logs at ${System.currentTimeMillis()}"
        //initiate a log to wait for
        Log.d("SendCrashActivity", logMessage)
    }

    override fun onStopped() {
    }

    override fun onLog(msg: String) {
        //Wait for the log to initiate a save to avoid ANR
        if (msg.contains(logMessage)) {
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage())
                Permissions.askWriteStoragePermission(this, false) { client.save() }
            else
                client.save()
        }
    }

    override fun onSaved(success: Boolean, path: String) {
        if (!success) {
            Snackbar.make(window.decorView, R.string.dump_logcat_failure, Snackbar.LENGTH_LONG).show()
            client.stop()
            return
        }
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val emailIntent = withContext(Dispatchers.IO) {
                client.stop()
                if (!::logcatZipPath.isInitialized) {
                    val externalPath = AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath
                        ?: return@withContext null
                    logcatZipPath = "$externalPath/logcat.zip"
                }
                val filesToAdd = mutableListOf(path)
                //add previous crash logs
                try {
                    AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath?.let { folder ->
                        File(folder).listFiles()?.forEach {
                            if (it.isFile && (it.name.contains("crash") || it.name.contains("logcat"))) filesToAdd.add(it.path)
                        }
                    }
                } catch (exception: IOException) {

                }

                FileUtils.zip(filesToAdd.toTypedArray(), logcatZipPath)
                try {
                    filesToAdd.forEach { FileUtils.deleteFile(it) }
                } catch (exception: IOException) {
                }

                val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
                emailIntent.type = "message/rfc822"
                //get medialib db if needed
                val attachments = ArrayList<Uri>()
                if (binding.includeMedialibSwitch.isChecked) {
                    if (!::dbPath.isInitialized) {
                        val externalPath = AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath
                            ?: return@withContext null
                        dbPath = "$externalPath/${Medialibrary.VLC_MEDIA_DB_NAME}"
                        dbZipPath = "$externalPath/db.zip"
                    }
                    val db = File(getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
                    val dbFile = File(dbPath)
                    FileUtils.copyFile(db, dbFile)
                    FileUtils.zip(arrayOf(dbPath), dbZipPath)
                    FileUtils.deleteFile(dbFile)

                    attachments.add(FileProvider.getUriForFile(this@SendCrashActivity, applicationContext.packageName + ".provider", File(dbZipPath)))
                }
                val appData = StringBuilder()
                try {
                    appData.append("App version: ${BuildConfig.VLC_VERSION_NAME}<br/>App version code: ${BuildConfig.VLC_VERSION_CODE}<br/>")
                } catch (e: PackageManager.NameNotFoundException) {

                }
                appData.append("Time: " + DateFormat.format("MM/dd/yyyy kk:mm:ss", System.currentTimeMillis()) + "<br/>")
                appData.append("Device model: ${Build.MANUFACTURER} ${Build.MODEL}<br/>")
                appData.append("Android version: ${Build.VERSION.SDK_INT}<br/>")
                appData.append("System name: ${Build.DISPLAY}<br/>")
                appData.append("Memory free: ${Formatter.formatFileSize(this@SendCrashActivity, AppUtils.freeMemory())} on ${Formatter.formatFileSize(this@SendCrashActivity, AppUtils.totalMemory())}")

                attachments.add(FileProvider.getUriForFile(this@SendCrashActivity, applicationContext.packageName + ".provider", File(logcatZipPath)))
                emailIntent.putExtra(Intent.EXTRA_STREAM, attachments)
                emailIntent.type = "application/zip"

                val describeCrash = if (::errMsg.isInitialized) {
                    "$errCtx:\n$errMsg"
                } else {
                    getString(R.string.describe_crash)
                }
                val body = "<p>Here are my crash logs for VLC</strong></p><p style=3D\"color:#16171A;\"> [$describeCrash]</p><p>$appData</p>"
                val htmlBody = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY)
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("vlc.crashreport+androidcrash@gmail.com"))
                val subject = if (::errMsg.isInitialized) "[${BuildConfig.VLC_VERSION_NAME}] Medialibrary uncaught exception!"
                else "[${BuildConfig.VLC_VERSION_NAME}] Crash logs for VLC"
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
                emailIntent.putExtra(Intent.EXTRA_TEXT, htmlBody)
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                emailIntent
            }
            emailIntent?.let { startActivity(it) }
            finish()
        }
    }

    private lateinit var client: DebugLogService.Client
    private lateinit var binding: SendCrashActivityBinding
    private lateinit var dbPath : String
    private lateinit var dbZipPath : String
    private lateinit var logcatZipPath : String
    private lateinit var errMsg : String
    private lateinit var errCtx : String

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.send_crash_activity)

        binding.reportBugButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://forum.videolan.org/viewforum.php?f=35".toUri()))
            finish()
        }
        binding.reportCrashButton.setOnClickListener {
            binding.crashFirstStepContainer.visibility = View.GONE
            binding.crashSecondStepContainer.visibility = View.VISIBLE
            client = DebugLogService.Client(this, this)
        }

        binding.sendCrashButton.setOnClickListener {
            client.start()
            binding.sendCrashButton.visibility = View.GONE
            binding.sendCrashProgress.visibility = View.VISIBLE
        }
        errMsg = intent.extras?.getString(CRASH_ML_MSG) ?: return
        errCtx = intent.extras?.getString(CRASH_ML_CTX) ?: return
        binding.crashFirstStepContainer.visibility = View.GONE
        binding.crashSecondStepContainer.visibility = View.VISIBLE
        binding.includeMedialibSwitch.isChecked = true
        client = DebugLogService.Client(this, this)
    }

    override fun onDestroy() {
        job?.complete()
        job = null
        if (::client.isInitialized) client.release()
        super.onDestroy()
    }

    companion object {
        var job : CompletableJob? = null
    }
}
