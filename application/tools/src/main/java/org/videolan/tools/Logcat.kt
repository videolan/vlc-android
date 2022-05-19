/*
 * ************************************************************************
 *  Logcat.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

/*****************************************************************************
 * Logcat.java
 *
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
 */

package org.videolan.tools

import java.io.*

class Logcat : Runnable {
    private var mCallback: Callback? = null
    private var mThread: Thread? = null
    private var mProcess: Process? = null
    private var mRun = false

    interface Callback {
        fun onLog(log: String)
    }

    override fun run() {
        val args = arrayOf("logcat", "-v", "time")
        var input: InputStreamReader? = null
        var br: BufferedReader? = null
        try {
            synchronized(this) {
                if (!mRun)
                    return
                mProcess = Runtime.getRuntime().exec(args)
                input = InputStreamReader(
                        mProcess!!.inputStream)
            }
            br = BufferedReader(input)
            var line = br.readLine()

            while (line != null) {
                mCallback!!.onLog(line)
                line = br.readLine()
            }
        } catch (e: IOException) {
        } finally {
            CloseableUtils.close(input)
            CloseableUtils.close(br)
        }
    }

    /**
     * Start a thread that will send logcat via a callback
     * @param callback
     */
    @Synchronized
    fun start(callback: Callback?) {
        if (callback == null)
            throw IllegalArgumentException("callback should not be null")
        if (mThread != null || mProcess != null)
            throw IllegalStateException("logcat is already started")
        mCallback = callback
        mRun = true
        mThread = Thread(this)
        mThread!!.start()
    }

    /**
     * Stop the thread previously started
     */
    @Synchronized
    fun stop() {
        mRun = false
        if (mProcess != null) {
            mProcess!!.destroy()
            mProcess = null
        }
        try {
            mThread!!.join()
        } catch (e: InterruptedException) {
        }

        mThread = null
        mCallback = null
    }

    companion object {
        const val TAG = "VLC/UiTools/Logcat"

        /**
         * Writes the current app logcat to a file.
         *
         * @param filename The filename to save it as
         * @throws IOException
         */
        @Throws(IOException::class)
        fun writeLogcat(filename: String) {
            val args = arrayOf("logcat", "-v", "time", "-d")

            val process = Runtime.getRuntime().exec(args)

            val input = InputStreamReader(process.inputStream)

            val fileStream: FileOutputStream
            try {
                fileStream = FileOutputStream(filename)
            } catch (e: FileNotFoundException) {
                return
            }

            val output = OutputStreamWriter(fileStream)
            val br = BufferedReader(input)
            val bw = BufferedWriter(output)

            try {
                var line = br.readLine()
                while (line != null) {
                    bw.write(line)
                    bw.newLine()
                    line = br.readLine()
                }
            } catch (e: Exception) {
            } finally {
                CloseableUtils.close(bw)
                CloseableUtils.close(output)
                CloseableUtils.close(br)
                CloseableUtils.close(input)
            }
        }

        /**
         * Get the last 500 lines of the application logcat.
         *
         * @return the log string.
         * @throws IOException
         */
        val logcat: String
            @Throws(IOException::class)
            get() {
                val args = arrayOf("logcat", "-v", "time", "-d", "-t", "500")

                val process = Runtime.getRuntime().exec(args)
                val input = InputStreamReader(
                        process.inputStream)
                val br = BufferedReader(input)
                val log = StringBuilder()
                var line = br.readLine()

                while (line != null) {
                    log.append(line + "\n")
                    line = br.readLine()
                }

                CloseableUtils.close(br)
                CloseableUtils.close(input)

                return log.toString()
            }
    }
}
