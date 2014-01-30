/*****************************************************************************
 * VlcCrashHandler.java
 *****************************************************************************
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
 *****************************************************************************/

package org.videolan.vlc;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;

public class VlcCrashHandler implements UncaughtExceptionHandler {

    private static final String TAG = "VLC/VlcCrashHandler";

    private UncaughtExceptionHandler defaultUEH;

    public VlcCrashHandler() {
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        // Inject some info about android version and the device, since google can't provide them in the developer console
        StackTraceElement[] trace = ex.getStackTrace();
        StackTraceElement[] trace2 = new StackTraceElement[trace.length+3];
        System.arraycopy(trace, 0, trace2, 0, trace.length);
        trace2[trace.length+0] = new StackTraceElement("Android", "MODEL", android.os.Build.MODEL, -1);
        trace2[trace.length+1] = new StackTraceElement("Android", "VERSION", android.os.Build.VERSION.RELEASE, -1);
        trace2[trace.length+2] = new StackTraceElement("Android", "FINGERPRINT", android.os.Build.FINGERPRINT, -1);
        ex.setStackTrace(trace2);

        ex.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();
        Log.e(TAG, stacktrace);

        // Save the log on SD card if available
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String sdcardPath = Environment.getExternalStorageDirectory().getPath();
            writeLog(stacktrace, sdcardPath + "/vlc_crash");
            writeLogcat(sdcardPath + "/vlc_logcat");
        }

        defaultUEH.uncaughtException(thread, ex);
    }

    private void writeLog(String log, String name) {
        CharSequence timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis());
        String filename = name + "_" + timestamp + ".log";

        try {
            FileOutputStream stream = new FileOutputStream(filename);
            OutputStreamWriter output = new OutputStreamWriter(stream);
            BufferedWriter bw = new BufferedWriter(output);

            bw.write(log);
            bw.newLine();

            bw.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeLogcat(String name) {
        CharSequence timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis());
        String filename = name + "_" + timestamp + ".log";
        try {
            Util.writeLogcat(filename);
        } catch (IOException e) {
            Log.e(TAG, "Cannot write logcat to disk");
        }
    }
}
