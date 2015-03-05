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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCrashHandler;
import org.videolan.vlc.gui.NativeCrashActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class VLCInstance {
    public final static String TAG = "VLC/Util/VLCInstance";

    /** A set of utility functions for the VLC application */
    public static LibVLC getLibVlcInstance() throws LibVlcException {
        LibVLC instance = LibVLC.getExistingInstance();
        if (instance == null) {
            Thread.setDefaultUncaughtExceptionHandler(new VLCCrashHandler());

            instance = LibVLC.getInstance();
            final Context context = VLCApplication.getAppContext();
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            VLCInstance.updateLibVlcSettings(pref);
            instance.init(context);
            LibVLC.setOnNativeCrashListener(new LibVLC.OnNativeCrashListener() {
                @Override
                public void onNativeCrash() {
                    Intent i = new Intent(context, NativeCrashActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.putExtra("PID", android.os.Process.myPid());
                    context.startActivity(i);
                }
            });
        }
        return instance;
    }

    public static void updateLibVlcSettings(SharedPreferences pref) {
        LibVLC instance = LibVLC.getExistingInstance();
        if (instance == null)
            return;

        instance.setSubtitlesEncoding(pref.getString("subtitle_text_encoding", ""));
        instance.setTimeStretching(pref.getBoolean("enable_time_stretching_audio", false));
        instance.setFrameSkip(pref.getBoolean("enable_frame_skip", false));
        instance.setChroma(pref.getString("chroma_format", ""));
        instance.setVerboseMode(pref.getBoolean("enable_verbose_mode", true));

        if (pref.getBoolean("equalizer_enabled", false))
            instance.setEqualizer(Preferences.getFloatArray(pref, "equalizer_values"));

        int aout;
        try {
            aout = Integer.parseInt(pref.getString("aout", "-1"));
        }
        catch (NumberFormatException nfe) {
            aout = -1;
        }
        int vout;
        try {
            vout = Integer.parseInt(pref.getString("vout", "-1"));
        }
        catch (NumberFormatException nfe) {
            vout = -1;
        }
        int deblocking;
        try {
            deblocking = Integer.parseInt(pref.getString("deblocking", "-1"));
        }
        catch(NumberFormatException nfe) {
            deblocking = -1;
        }
        int hardwareAcceleration;
        try {
            hardwareAcceleration = Integer.parseInt(pref.getString("hardware_acceleration", "-1"));
        }
        catch(NumberFormatException nfe) {
            hardwareAcceleration = -1;
        }
        int devHardwareDecoder;
        try {
            devHardwareDecoder = Integer.parseInt(pref.getString("dev_hardware_decoder", "-1"));
        }
        catch(NumberFormatException nfe) {
            devHardwareDecoder = -1;
        }
        int networkCaching = pref.getInt("network_caching_value", 0);
        if(networkCaching > 60000)
            networkCaching = 60000;
        else if(networkCaching < 0)
            networkCaching = 0;
        instance.setAout(aout);
        instance.setVout(vout);
        instance.setDeblocking(deblocking);
        instance.setNetworkCaching(networkCaching);
        instance.setHardwareAcceleration(hardwareAcceleration);
        instance.setDevHardwareDecoder(devHardwareDecoder);
    }


}
