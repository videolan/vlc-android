/*****************************************************************************
 * AudioUtil.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.audio;

import java.io.File;

import org.videolan.vlc.Media;
import org.videolan.vlc.Util;

import android.app.Activity;
import android.content.ContentValues;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore;

public class AudioUtil {

    public static void setRingtone( Media song, Activity activity){
        File newringtone = Util.URItoFile(song.getLocation());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, newringtone.getAbsolutePath());
        values.put(MediaStore.MediaColumns.TITLE, song.getTitle());
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/*");
        values.put(MediaStore.Audio.Media.ARTIST, song.getArtist());
        values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
        values.put(MediaStore.Audio.Media.IS_ALARM, false);
        values.put(MediaStore.Audio.Media.IS_MUSIC, false);
        Uri uri = MediaStore.Audio.Media.getContentUriForPath(newringtone.getAbsolutePath());
        activity.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=\"" + newringtone.getAbsolutePath() + "\"", null);
        Uri newUri = activity.getContentResolver().insert(uri, values);
        RingtoneManager.setActualDefaultRingtoneUri(
                activity.getApplicationContext(),
                RingtoneManager.TYPE_RINGTONE,
                newUri
                );
    }
}
