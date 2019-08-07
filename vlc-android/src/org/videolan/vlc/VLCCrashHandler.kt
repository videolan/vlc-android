/*****************************************************************************
 * VlcCrashHandler.java
 *
 * Copyright © 2012 VLC authors and VideoLAN
 *
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
 */

package org.videolan.vlc

import android.os.Environment
import android.text.format.DateFormat
import android.util.Log
import org.videolan.vlc.util.Logcat
import org.videolan.vlc.util.Util
import java.io.*
import java.lang.Thread.UncaughtExceptionHandler

class VLCCrashHandler : UncaughtExceptionHandler {

    private val defaultUEH: UncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {

        val result = StringWriter()
        val printWriter = PrintWriter(result)

        // Inject some info about android version and the device, since google can't provide them in the developer console
        val trace = ex.stackTrace
        val trace2 = arrayOfNulls<StackTraceElement>(trace.size + 3)
        System.arraycopy(trace, 0, trace2, 0, trace.size)
        trace2[trace.size + 0] = StackTraceElement("Android", "MODEL", android.os.Build.MODEL, -1)
        trace2[trace.size + 1] = StackTraceElement("Android", "VERSION", android.os.Build.VERSION.RELEASE, -1)
        trace2[trace.size + 2] = StackTraceElement("Android", "FINGERPRINT", android.os.Build.FINGERPRINT, -1)
        ex.stackTrace = trace2

        ex.printStackTrace(printWriter)
        val stacktrace = result.toString()
        printWriter.close()
        Log.e(TAG, stacktrace)

        // Save the log on SD card if available
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            writeLog(stacktrace, VLCApplication.appContext.getExternalFilesDir(null)!!.absolutePath + "/vlc_crash")
            writeLogcat(VLCApplication.appContext.getExternalFilesDir(null)!!.absolutePath + "/vlc_logcat")
        }

        defaultUEH.uncaughtException(thread, ex)
    }


    private fun writeLog(log: String, name: String) {
        val timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = name + "_" + timestamp + ".log"

        val stream: FileOutputStream
        try {
            stream = FileOutputStream(filename)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        }

        val output = OutputStreamWriter(stream)
        val bw = BufferedWriter(output)

        try {
            bw.write(log)
            bw.newLine()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            Util.close(bw)
            Util.close(output)
        }
    }

    private fun writeLogcat(name: String) {
        val timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = name + "_" + timestamp + ".log"
        try {
            Logcat.writeLogcat(filename)
        } catch (e: IOException) {
            Log.e(TAG, "Cannot write logcat to disk")
        }

    }

    companion object {

        private val TAG = "VLC/VlcCrashHandler"
    }
}
