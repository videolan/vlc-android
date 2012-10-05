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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    private static Bitmap getCoverFromMediaStore(Context context, Media media) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(uri, new String[] {
                       MediaStore.Audio.Albums.ALBUM,
                       MediaStore.Audio.Albums.ALBUM_ART },
                       MediaStore.Audio.Albums.ALBUM + " LIKE ?",
                       new String[] { media.getAlbum() }, null);
        if (cursor == null) {
            // do nothing
        } else if (!cursor.moveToFirst()) {
            // do nothing
            cursor.close();
        } else {
            int titleColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Albums.ALBUM_ART);
            String albumArt = cursor.getString(titleColumn);
            cursor.close();
            if(albumArt != null) { // could be null (no album art stored)
                Bitmap b = BitmapFactory.decodeFile(albumArt);
                if (b != null)
                    return b;
            }
        }
        return null;
    }

    @SuppressLint("SdCardPath")
    private static Bitmap getCoverFromVlc(Context context, Media media) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String artworkURL = media.getArtworkURL();
        final String cacheDir = "/sdcard/Android/data/org.videolan.vlc/cache";
        if (artworkURL != null && artworkURL.startsWith("file://")) {
            return BitmapFactory.decodeFile(Uri.decode(artworkURL).replace("file://", ""));
        } else if(artworkURL != null && artworkURL.startsWith("attachment://")) {
            // Decode if the album art is embedded in the file
            String mArtist = media.getArtist();
            String mAlbum = media.getAlbum();

            /* Parse decoded attachment */
            if( mArtist.length() == 0 || mAlbum.length() == 0 ||
                mArtist.equals(VLCApplication.getAppContext().getString(R.string.unknown_artist)) ||
                mAlbum.equals(VLCApplication.getAppContext().getString(R.string.unknown_album)) )
            {
                /* If artist or album are missing, it was cached by title MD5 hash */
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] binHash = md.digest((artworkURL + media.getTitle()).getBytes("UTF-8"));
                /* Convert binary hash to normal hash */
                BigInteger hash = new BigInteger(1, binHash);
                String titleHash = hash.toString(16);
                while(titleHash.length() < 32) {
                    titleHash = "0" + titleHash;
                }
                /* Use generated hash to find art */
                artworkURL = cacheDir + "/art/arturl/" + titleHash + "/art.png";
            } else {
                /* Otherwise, it was cached by artist and album */
                artworkURL = cacheDir + "/art/artistalbum/" + mArtist + "/" + mAlbum + "/art.png";
            }

            return BitmapFactory.decodeFile(artworkURL);
        }
        return null;
    }

    private static Bitmap getCoverFromFolder(Context context, Media media) {
        File f = Util.URItoFile(media.getLocation());
        for (File s : f.getParentFile().listFiles()) {
            if (s.getAbsolutePath().endsWith("png") ||
                    s.getAbsolutePath().endsWith("jpg"))
                return BitmapFactory.decodeFile(s.getAbsolutePath());
        }
        return null;
    }

    public static Bitmap getCover(Context context, Media media, int width) {
        Bitmap cover = null;
        try {
            // try to get the cover from android MediaStore
            cover = getCoverFromMediaStore(context, media);

            //cover not in MediaStore, trying vlc
            if (cover == null)
                cover = getCoverFromVlc(context, media);

            //still no cover found, looking in folder ...
            if (cover == null)
                cover = getCoverFromFolder(context, media);

            //scale down if requested
            if (cover != null && width > 0)
                cover = Util.scaleDownBitmap(context, cover, width);
        } catch (Exception e) {
        }
        return cover;
    }
}
