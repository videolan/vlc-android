/*****************************************************************************
 * VLCInstance.java
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCrashHandler;
import org.videolan.vlc.gui.CompatErrorActivity;

public class VLCInstance {
    public final static String TAG = "VLC/UiTools/VLCInstance";

    private static LibVLC sLibVLC = null;

    private static Runnable sCopyLua = new Runnable() {
        @Override
        public void run() {
            final String destinationFolder = VLCApplication.getAppContext().getDir("vlc",
                    Context.MODE_PRIVATE).getAbsolutePath() + "/.share/lua";
            AssetManager am = VLCApplication.getAppResources().getAssets();
            FileUtils.copyAssetFolder(am, "lua", destinationFolder);
        }
    };

    /** A set of utility functions for the VLC application */
    public synchronized static LibVLC get() throws IllegalStateException {
        if (sLibVLC == null) {
            Thread.setDefaultUncaughtExceptionHandler(new VLCCrashHandler());

            final Context context = VLCApplication.getAppContext();
            if(!VLCUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, VLCUtil.getErrorMsg());
                throw new IllegalStateException("LibVLC initialisation failed: " + VLCUtil.getErrorMsg());
            }

            sLibVLC = new LibVLC(context, VLCOptions.getLibOptions());
            VLCApplication.runBackground(sCopyLua);
        }
        return sLibVLC;
    }

    public static synchronized void restart() throws IllegalStateException {
        if (sLibVLC != null) {
            sLibVLC.release();
            sLibVLC = new LibVLC(VLCApplication.getAppContext(), VLCOptions.getLibOptions());
        }
    }

    public static synchronized boolean testCompatibleCPU(Context context) {
        if (sLibVLC == null && !VLCUtil.hasCompatibleCPU(context)) {
            if (context instanceof Activity) {
                final Intent i = new Intent(context, CompatErrorActivity.class);
                context.startActivity(i);
            }
            return false;
        } else
            return true;
    }
}
