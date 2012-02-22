package org.videolan.vlc.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

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
        ex.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();

        Log.e(TAG, stacktrace);
        writeLog(stacktrace, "//sdcard//vlc_crash");
        writeLogcat("//sdcard//vlc_logcat");

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
        String[] args = { "logcat", "-v", "time", "-d" };

        try {
            Process process = Runtime.getRuntime().exec(args);
            InputStreamReader input = new InputStreamReader(
                    process.getInputStream());
            OutputStreamWriter output = new OutputStreamWriter(
                    new FileOutputStream(filename));
            BufferedReader br = new BufferedReader(input);
            BufferedWriter bw = new BufferedWriter(output);
            String line;

            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
            }

            bw.close();
            output.close();
            br.close();
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
