/*****************************************************************************
 * Util.java
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.audio.AudioServiceController;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.text.TextUtils.TruncateAt;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

public class Util {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);
    public static final String ACTION_SCAN_START = "org.videolan.vlc.gui.ScanStart";
    public static final String ACTION_SCAN_STOP = "org.videolan.vlc.gui.ScanStop";

    /** Print an on-screen message to alert the user */
    public static void toaster(Context context, int stringId) {
        Toast.makeText(context, stringId, Toast.LENGTH_SHORT).show();
    }

    public static int convertPxToDp(int px) {
        DisplayMetrics metrics = VLCApplication.getAppResources().getDisplayMetrics();
        float logicalDensity = metrics.density;
        int dp = Math.round(px / logicalDensity);
        return dp;
    }

    public static int convertDpToPx(int dp) {
        return Math.round(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                        VLCApplication.getAppResources().getDisplayMetrics())
                );
    }

    public static String readAsset(String assetName, String defaultS) {
        try {
            InputStream is = VLCApplication.getAppResources().getAssets().open(assetName);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF8"));
            StringBuilder sb = new StringBuilder();
            String line = r.readLine();
            if(line != null) {
                sb.append(line);
                line = r.readLine();
                while(line != null) {
                    sb.append('\n');
                    sb.append(line);
                    line = r.readLine();
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return defaultS;
        }
    }

    /**
     * Retrieve the existing media object from the media library or create a
     * new one from the given MRL.
     *
     * @param libVLC LibVLC instance
     * @param mrl MRL of the media
     * @return A media object from the media library or newly created
     */
    public static Media getOrCreateMedia(LibVLC libVLC, String mrl) {
        Media mlItem = MediaLibrary.getInstance().getMediaItem(mrl);
        if(mlItem == null)
            mlItem = new Media(libVLC, mrl);
        return mlItem;
    }

    /**
     * Get a resource id from an attribute id.
     * @param context
     * @param attrId
     * @return the resource id
     */
    public static int getResourceFromAttribute(Context context, int attrId) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[] {attrId});
        int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }

    /**
     * Set the alignment mode of the specified TextView with the desired align
     * mode from preferences.
     *
     * See @array/audio_title_alignment_values
     *
     * @param alignMode Align mode as read from preferences
     * @param t Reference to the textview
     */
    public static void setAlignModeByPref(int alignMode, TextView t) {
        if(alignMode == 1)
            t.setEllipsize(TruncateAt.END);
        else if(alignMode == 2)
            t.setEllipsize(TruncateAt.START);
        else if(alignMode == 3) {
            t.setEllipsize(TruncateAt.MARQUEE);
            t.setMarqueeRepeatLimit(-1);
            t.setSelected(true);
        }
    }

    /**
     * Generate a value suitable for use in {@link #setId(int)}.
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        for (;;) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    public static void actionScanStart() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_START);
        VLCApplication.getAppContext().sendBroadcast(intent);
    }

    public static void actionScanStop() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_STOP);
        VLCApplication.getAppContext().sendBroadcast(intent);
    }


    public static void openStream(Context context, final String uri){
        VLCCallbackTask task = new VLCCallbackTask(context){
            @Override
            public void run() {
                AudioServiceController c = AudioServiceController.getInstance();

                      /* Use the audio player by default. If a video track is
                       * detected, then it will automatically switch to the video
                       * player. This allows us to support more types of streams
                       * (for example, RTSP and TS streaming) where ES can be
                       * dynamically adapted rather than a simple scan.
                       */
                c.load(uri, false);
            }
        };
        task.execute();
    }
}
