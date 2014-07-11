/*****************************************************************************
 * Logcat.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Logcat {
    public final static String TAG = "VLC/Util/Logcat";

    /**
     * Writes the current app logcat to a file.
     *
     * @param filename The filename to save it as
     * @throws IOException
     */
    public static void writeLogcat(String filename) throws IOException {
        String[] args = { "logcat", "-v", "time", "-d" };

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

        br.close();
        input.close();

        return log.toString();
    }

}
