/*****************************************************************************
 * StartActivityOnCrash.java
 *
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
 */

package org.videolan.vlc.gui.video.benchmark

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class StartActivityOnCrash internal constructor(private val context: Activity) : Thread.UncaughtExceptionHandler {

    private val preferences: SharedPreferences

    init {

        preferences = context.getSharedPreferences(SHARED_PREFERENCE, Context.MODE_WORLD_READABLE)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        var exceptionMessage = throwable.message

        //see TransactionTooLargeException
        if (exceptionMessage?.length ?: 0 > MAX_STACK_TRACE_SIZE)
            exceptionMessage = exceptionMessage?.substring(0, MAX_STACK_TRACE_SIZE - 3) + "..."

        preferences.edit(commit = true) {
            putString(SHARED_PREFERENCE_STACK_TRACE, exceptionMessage)
        }
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(10)
    }

    companion object {

        private const val SHARED_PREFERENCE = "org.videolab.vlc.gui.video.benchmark.UNCAUGHT_EXCEPTIONS"
        private const val SHARED_PREFERENCE_STACK_TRACE = "org.videolab.vlc.gui.video.benchmark.STACK_TRACE"

        private const val MAX_STACK_TRACE_SIZE = 131071 //128 KB - 1

        fun setUp(context: Activity): Boolean {
            try {
                Thread.setDefaultUncaughtExceptionHandler(StartActivityOnCrash(context))
            } catch (e: Exception) {
                return false
            }

            return true
        }
    }
}
