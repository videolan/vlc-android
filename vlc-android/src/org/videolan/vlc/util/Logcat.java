/*****************************************************************************
 * Logcat.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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

package org.videolan.vlc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Logcat implements Runnable {
    public final static String TAG = "VLC/Util/Logcat";
    private Callback mCallback = null;
    private Thread mThread = null;
    private Process mProcess = null;
    private boolean mRun = false;

    public interface Callback {
        public void onLog(String log);
    }

    public Logcat() {
    }

    @Override
    public void run() {
        final String[] args = { "logcat", "-v", "time" };
        InputStreamReader input = null;
        BufferedReader br = null;
        try {
            synchronized (this) {
                if (!mRun)
                    return;
                mProcess = Runtime.getRuntime().exec(args);
                input = new InputStreamReader(
                        mProcess.getInputStream());
            }
            br = new BufferedReader(input);
            String line;

            while ((line = br.readLine()) != null)
                mCallback.onLog(line);

        } catch (IOException e) {
        } finally {
            Util.close(input);
            Util.close(br);
        }
    }

    /**
     * Start a thread that will send logcat via a callback
     * @param callback
     */
    public synchronized void start(Callback callback) {
        if (callback == null)
            throw new IllegalArgumentException("callback should not be null");
        if (mThread != null || mProcess != null)
            throw new IllegalStateException("logcat is already started");
        mCallback = callback;
        mRun = true;
        mThread = new Thread(this);
        mThread.start();
    }

    /**
     * Stop the thread previously started
     */
    public synchronized void stop() {
        mRun = false;
        if (mProcess != null) {
            mProcess.destroy();
            mProcess = null;
        }
        try {
            mThread.join();
        } catch (InterruptedException e) {
        }
        mThread = null;
        mCallback = null;
    }

    /**
     * Writes the current app logcat to a file.
     *
     * @param filename The filename to save it as
     * @throws IOException
     */
    public static void writeLogcat(String filename) throws IOException {
        String[] args = { "logcat", "-v", "time", "-d" };

        Process process = Runtime.getRuntime().exec(args);

        InputStreamReader input = new InputStreamReader(process.getInputStream());

        FileOutputStream fileStream;
        try {
            fileStream = new FileOutputStream(filename);
        } catch( FileNotFoundException e) {
            return;
        }

        OutputStreamWriter output = new OutputStreamWriter(fileStream);
        BufferedReader br = new BufferedReader(input);
        BufferedWriter bw = new BufferedWriter(output);

        try {
            String line;
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
            }
        }catch(Exception e) {}
        finally {
            Util.close(bw);
            Util.close(output);
            Util.close(br);
            Util.close(input);
        }
    }

    /**
     * Get the last 500 lines of the application logcat.
     *
     * @return the log string.
     * @throws IOException
     */
    public static String getLogcat() throws IOException {
        String[] args = { "logcat", "-v", "time", "-d", "-t", "500" };

        Process process = Runtime.getRuntime().exec(args);
        InputStreamReader input = new InputStreamReader(
                process.getInputStream());
        BufferedReader br = new BufferedReader(input);
        StringBuilder log = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null)
            log.append(line + "\n");

        Util.close(br);
        Util.close(input);

        return log.toString();
    }
}
