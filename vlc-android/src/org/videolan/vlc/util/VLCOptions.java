/*****************************************************************************
 * VLCConfig.java
 *****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.HWDecoderUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import java.util.ArrayList;


public class VLCOptions {
    private static final String TAG = "VLCConfig";

    public static final int AOUT_AUDIOTRACK = 0;
    public static final int AOUT_OPENSLES = 1;

    public static final int HW_ACCELERATION_AUTOMATIC = -1;
    public static final int HW_ACCELERATION_DISABLED = 0;
    public static final int HW_ACCELERATION_DECODING = 1;
    public static final int HW_ACCELERATION_FULL = 2;

    public final static int MEDIA_NO_VIDEO = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;

    private static final String DEFAULT_CODEC_LIST = "mediacodec_ndk,mediacodec_jni,iomx,all";

    private static float[] sEqualizer = null;
    private static boolean sHdmiAudioEnabled = false;

    public static ArrayList<String> getLibOptions(SharedPreferences pref) {
        ArrayList<String> options = new ArrayList<String>(50);

        final boolean timeStrechingDefault = VLCApplication.getAppResources().getBoolean(R.bool.time_stretching_default);
        final boolean timeStreching = pref.getBoolean("enable_time_stretching_audio", timeStrechingDefault);
        final String subtitlesEncoding = pref.getString("subtitle_text_encoding", "");
        final boolean frameSkip = pref.getBoolean("enable_frame_skip", false);
        String chroma = pref.getString("chroma_format", "");
        chroma = chroma.equals("YV12") && !AndroidUtil.isGingerbreadOrLater() ? "" : chroma;
        final boolean verboseMode = pref.getBoolean("enable_verbose_mode", true);

        if (pref.getBoolean("equalizer_enabled", false))
            setEqualizer(Preferences.getFloatArray(pref, "equalizer_values"));

        int aout = -1;
        try {
            aout = Integer.parseInt(pref.getString("aout", "-1"));
        } catch (NumberFormatException nfe) {
        }
        aout = getAout(aout);

        int deblocking = -1;
        try {
            deblocking = getDeblocking(Integer.parseInt(pref.getString("deblocking", "-1")));
        } catch (NumberFormatException nfe) {
        }

        int networkCaching = pref.getInt("network_caching_value", 0);
        if (networkCaching > 60000)
            networkCaching = 60000;
        else if (networkCaching < 0)
            networkCaching = 0;

        /* CPU intensive plugin, setting for slow devices */
        options.add(timeStreching ? "--audio-time-stretch" : "--no-audio-time-stretch");
        options.add("--avcodec-skiploopfilter");
        options.add("" + deblocking);
        options.add("--avcodec-skip-frame");
        options.add(frameSkip ? "2" : "0");
        options.add("--avcodec-skip-idct");
        options.add(frameSkip ? "2" : "0");
        options.add("--subsdec-encoding");
        options.add(subtitlesEncoding);
        options.add("--stats");
        /* XXX: why can't the default be fine ? #7792 */
        if (networkCaching > 0)
            options.add("--network-caching=" + networkCaching);
        options.add(aout == AOUT_OPENSLES ? "--aout=opensles" : (aout == AOUT_AUDIOTRACK ? "--aout=android_audiotrack" : "--aout=dummy"));
        options.add("--androidsurface-chroma");
        options.add(chroma.indexOf(0) == 0 ? chroma : "RV32");

        if (sHdmiAudioEnabled) {
            options.add("--spdif");
            options.add("--audiotrack-audio-channels");
            options.add("8"); // 7.1 maximum
        }
        options.add(verboseMode ? "-vvv" : "-vv");
        return options;
    }

    private static int getAout(int aout) {
        final HWDecoderUtil.AudioOutput hwaout = HWDecoderUtil.getAudioOutputFromDevice();
        if (hwaout == HWDecoderUtil.AudioOutput.AUDIOTRACK || hwaout == HWDecoderUtil.AudioOutput.OPENSLES)
            return hwaout == HWDecoderUtil.AudioOutput.OPENSLES ? AOUT_OPENSLES : AOUT_AUDIOTRACK;

        return aout == AOUT_OPENSLES ? AOUT_OPENSLES : AOUT_AUDIOTRACK;
    }

    private static int getDeblocking(int deblocking) {
        int ret = deblocking;
        if (deblocking < 0) {
            /**
             * Set some reasonable sDeblocking defaults:
             *
             * Skip all (4) for armv6 and MIPS by default
             * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
             * Skip non-key (3) for all devices that don't meet anything above
             */
            VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
            if (m == null)
                return ret;
            if ((m.hasArmV6 && !(m.hasArmV7)) || m.hasMips)
                ret = 4;
            else if (m.frequency >= 1200 && m.processors > 2)
                ret = 1;
            else if (m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1;
                Log.d(TAG, "Used bogoMIPS due to lack of frequency info");
            } else
                ret = 3;
        } else if (deblocking > 4) { // sanity check
            ret = 3;
        }
        return ret;
    }

    private static int getHardwareAcceleration(HWDecoderUtil.Decoder decoder, int hardwareAcceleration) {
        if (hardwareAcceleration == HW_ACCELERATION_DISABLED) {
            return HW_ACCELERATION_DISABLED;
        } else {
            // Automatic or forced
            if (decoder == HWDecoderUtil.Decoder.NONE ||
                    (decoder == HWDecoderUtil.Decoder.UNKNOWN && hardwareAcceleration < HW_ACCELERATION_DISABLED)) {
                return HW_ACCELERATION_DISABLED;
            } else {
                if (hardwareAcceleration <= HW_ACCELERATION_AUTOMATIC || hardwareAcceleration > HW_ACCELERATION_FULL)
                    return HW_ACCELERATION_FULL;
                else
                    return hardwareAcceleration;
            }
        }
    }

    public static void setMediaOptions(Media media, Context context, int flags) {
        boolean noHardwareAcceleration = (flags & MEDIA_NO_HWACCEL) != 0;
        boolean noVideo = (flags & MEDIA_NO_VIDEO) != 0;
        final boolean paused = (flags & MEDIA_PAUSED) != 0;
        int hardwareAcceleration = HW_ACCELERATION_DISABLED;

        if (!noHardwareAcceleration) {
            try {
                final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
                hardwareAcceleration = Integer.parseInt(pref.getString("hardware_acceleration", "-1"));
            } catch (NumberFormatException nfe) {
            }
        }
        if (hardwareAcceleration == HW_ACCELERATION_DISABLED)
            media.setHWDecoderEnabled(false, false);
        else if (hardwareAcceleration == HW_ACCELERATION_FULL || hardwareAcceleration == HW_ACCELERATION_DECODING) {
            media.setHWDecoderEnabled(true, true);
            if (hardwareAcceleration == HW_ACCELERATION_DECODING) {
                media.addOption(":no-mediacodec-dr");
                media.addOption(":no-omxil-dr");
            }
        } /* else automatic: use default options */

        if (noVideo)
            media.addOption(":no-video");
        if (paused)
            media.addOption(":start-paused");
    }

    // Equalizer
    public static synchronized float[] getEqualizer() {
        return sEqualizer;
    }

    public static synchronized void setEqualizer(float[] equalizer) {
        sEqualizer = equalizer;
    }

    public static synchronized void setAudioHdmiEnabled(boolean enabled) {
        sHdmiAudioEnabled = enabled;
    }

    public static synchronized boolean isAudioHdmiEnabled() {
        return sHdmiAudioEnabled;
    }
}