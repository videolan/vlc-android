/*****************************************************************************
 * StartActivityOnCrash.java
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

package org.videolan.vlc.gui.video.benchmark;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class StartActivityOnCrash implements Thread.UncaughtExceptionHandler {

    private static final String SHARED_PREFERENCE = "org.videolab.vlc.gui.video.benchmark.UNCAUGHT_EXCEPTIONS";
    private static final String SHARED_PREFERENCE_STACK_TRACE = "org.videolab.vlc.gui.video.benchmark.STACK_TRACE";

    private static final int MAX_STACK_TRACE_SIZE = 131071; //128 KB - 1

    private SharedPreferences preferences;
    private Activity context;

    StartActivityOnCrash(Activity context) {
        //noinspection deprecation
        preferences = context.getSharedPreferences(SHARED_PREFERENCE, Context.MODE_WORLD_READABLE);
        this.context = context;
    }

    @Override
    public void uncaughtException(Thread thread, final Throwable throwable) {
        String exceptionMessage = throwable.getMessage();

        //see TransactionTooLargeException
        if (exceptionMessage.length() > MAX_STACK_TRACE_SIZE)
            exceptionMessage = exceptionMessage.substring(0, MAX_STACK_TRACE_SIZE - 3) + "...";

        preferences.edit().putString(SHARED_PREFERENCE_STACK_TRACE, exceptionMessage).commit();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    public static boolean setUp(Activity context) {
        try {
            Thread.setDefaultUncaughtExceptionHandler(new StartActivityOnCrash(context));
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
