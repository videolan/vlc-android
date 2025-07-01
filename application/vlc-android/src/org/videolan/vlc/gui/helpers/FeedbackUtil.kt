/*
 * ************************************************************************
 *  FeedbackUtil.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCOptions
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.gui.preferences.search.PreferenceParser.getChangedPrefsJson
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import java.io.BufferedWriter
import java.io.File

object FeedbackUtil {
    /**
     * Send an email with the given parameters
     *
     * @param activity Activity to use
     * @param supportType hosts the email address to use
     * @param includeMedialibrary Whether to include the medialibrary database or not
     * @param message Message to send
     * @param subject Subject of the email
     * @param feedbackType Type of feedback
     * @param logcatZipPath Path to the logcat zip file
     * @return true if the email was sent, false otherwise
     */
    suspend fun sendEmail(activity: FragmentActivity, supportType: SupportType, includeMedialibrary: Boolean, message: String, subject: String, feedbackType: Int, logcatZipPath: String? = null): Boolean {
        val emailIntent = withContext(Dispatchers.IO) {

            val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            emailIntent.type = "message/rfc822"
            //get medialib db if needed
            val attachments = ArrayList<Uri>()
            if (includeMedialibrary) {
                val externalPath = AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath
                    ?: return@withContext null
                val dbPath = "$externalPath/${Medialibrary.VLC_MEDIA_DB_NAME}"
                val dbZipPath = "$externalPath/db.zip"
                val db = File(activity.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
                val dbFile = File(dbPath)
                FileUtils.copyFile(db, dbFile)
                FileUtils.zip(arrayOf(dbPath), dbZipPath)
                FileUtils.deleteFile(dbFile)

                attachments.add(FileProvider.getUriForFile(activity, activity.applicationContext.packageName + ".provider", File(dbZipPath)))
            }
            val appData = generateUsefulInfo(activity).replace("\r\n", "<br/>") + "<br/>"

            val body = "<p style=3D\"color:#16171A;\">$message</p><p>$appData</p>"

            logcatZipPath?.let {
                attachments.add(FileProvider.getUriForFile(activity, activity.applicationContext.packageName + ".provider", File(it)))
            }
            emailIntent.putExtra(Intent.EXTRA_STREAM, attachments)
            emailIntent.type = "application/zip"

            val htmlBody = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY)
            emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(supportType.email))
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, generateSubject(subject, feedbackType))
            emailIntent.putExtra(Intent.EXTRA_TEXT, htmlBody)
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            emailIntent
        }
        try {
            emailIntent?.let { activity.startActivity(it) }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun generateSubject(initialSubject: String, feedbackType: Int): String {
        val subjectPrepend = when (feedbackType) {
            0 -> "[Help] "
            1 -> "[Feedback/Request] "
            2 -> "[Bug] "
            3 -> "[Crash] "
            else -> "[ML Crash]"
        }
        return subjectPrepend + initialSubject
    }

    fun generateUsefulInfo(context: Context) = buildString {
        append("____________________________\r\n")
        append("Useful info\r\n")
        append("____________________________\r\n")
        append("App version: ${BuildConfig.VLC_VERSION_CODE} / ${BuildConfig.VLC_VERSION_NAME}\r\n")
        append("libvlc: ${BuildConfig.LIBVLC_VERSION}\r\n")
        append("libvlc revision: ${context.getString(R.string.build_libvlc_revision)}\r\n")
        append("vlc revision: ${context.getString(R.string.build_vlc_revision)}\r\n")
        append("medialibrary: ${BuildConfig.ML_VERSION}\r\n")
        append("Android version: ${Build.VERSION.SDK_INT}\r\n")
        append("System name: ${Build.DISPLAY}\r\n")
        append("Device Model: ${Build.MANUFACTURER} - ${Build.MODEL}\r\n")
        append("____________________________\r\n")
        append("Permissions\r\n")
        append("____________________________\r\n")
        append("Can read: ${Permissions.canReadStorage(context)}\r\n")
        append("Can write: ${Permissions.canWriteStorage(context)}\r\n")
        append("Storage ALL access: ${Permissions.hasAllAccess(context)}\r\n")
        append("Notifications: ${Permissions.canSendNotifications(context)}\r\n")
        append("PiP Allowed: ${Permissions.isPiPAllowed(context)}\r\n")
        try {
            append("____________________________\r\n")
            append("Changed settings:\r\n")
            append("____________________________\r\n")
            append("${PreferenceParser.getChangedPrefsString(context)}\r\n")
        } catch (e: Exception) {
            append("Cannot retrieve changed settings\r\n")
            append(Log.getStackTraceString(e))
        }
        append("____________________________\r\n")
        append("Settings export/import (copy this line in a file to restore those settings)\r\n")
        append("____________________________\r\n")
        append("${PreferenceParser.getChangedPrefsJson(context)}\r\n")
        append("____________________________\r\n")
        append("vlc options: ${VLCOptions.libOptions.joinToString(" ")}\r\n")
        append("____________________________\r\n")
    }

    enum class SupportType(val email: String) {
        SUPPORT_EMAIL("android-support@videolan.org"),
        CRASH_REPORT_EMAIL("vlc.crashreport+androidcrash@gmail.com")
    }

    fun getInstallSource(context: Context): Pair<String, String>? {
        val installerInfo: String = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            else
                context.packageManager.getInstallerPackageName(context.packageName)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            "unknown"
        } ?: "unknown"
        return when (installerInfo) {
            "com.android.vending" -> Pair ("https://play.google.com/store/apps/details?id=org.videolan.vlc", "Google Play")
            "com.amazon.venezia" -> Pair ("https://www.amazon.fr/VLC-Mobile-Team-for-Fire/dp/B00U65KQMQ", "Amazon AppStore")
            "com.huawei.appmarket" -> Pair ("https://appgallery.huawei.com/#/app/C101924579", "Huawei AppGallery")
            else -> null
        }
    }
}