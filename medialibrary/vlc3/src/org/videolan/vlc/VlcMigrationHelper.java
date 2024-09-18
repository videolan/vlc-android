/*
 * ************************************************************************
 *  VlcMigrationHelper.java
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc;

import static org.videolan.libvlc.util.AndroidUtil.isMarshMallowOrLater;

import android.os.Build;

import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.HWDecoderUtil;

import java.util.ArrayList;
import java.util.List;

public class VlcMigrationHelper {
    public static List<IMedia.Track> getMediaTracks(IMedia media) {
        ArrayList<IMedia.Track> result = new ArrayList<>();
        for (int i = 0; i < media.getTrackCount(); ++i) {
            result.add(media.getTrack(i));
        }
        return result;
    }

    public static final boolean isLolliPopOrLater = isMarshMallowOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    public static final boolean isKitKatOrLater = isLolliPopOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    public static final boolean isJellyBeanMR2OrLater = isKitKatOrLater || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;

    public enum AudioOutput {
        OPENSLES, AUDIOTRACK, ALL
    }

    public static AudioOutput getAudioOutputFromDevice() {
        HWDecoderUtil.AudioOutput aout = HWDecoderUtil.getAudioOutputFromDevice();
        if (aout == HWDecoderUtil.AudioOutput.OPENSLES)
            return AudioOutput.OPENSLES;
        else if (aout == HWDecoderUtil.AudioOutput.AUDIOTRACK)
            return AudioOutput.AUDIOTRACK;
        return AudioOutput.ALL;
    }
}
