/*****************************************************************************
 * MediaWrapperListPlayer.java
 *****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;

public class MediaWrapperListPlayer {

    private int mPlayerIndex = 0;

    final private MediaWrapperList mMediaList;

    private static MediaWrapperListPlayer sMediaWrapperListPlayer = null;

    public static synchronized MediaWrapperListPlayer getInstance() {
        if (sMediaWrapperListPlayer == null)
            sMediaWrapperListPlayer = new MediaWrapperListPlayer();
        return sMediaWrapperListPlayer;
    }

    public MediaWrapperListPlayer() {
        mMediaList = new MediaWrapperList();
    }

    public MediaWrapperList getMediaList() {
        return mMediaList;
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param position The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    public void playIndex(Context context, int position, int flags) {
        String mrl = mMediaList.getMRL(position);
        if (mrl == null)
            return;
        final MediaWrapper mw = mMediaList.getMedia(position);
        String[] options = VLCOptions.getMediaOptions(context, flags | (mw != null ? mw.getFlags() : 0));
        mPlayerIndex = position;

        final Media media = new Media(VLCInstance.get(), mw.getUri());
        for (String option : options)
            media.addOption(option);
        VLCInstance.getMainMediaPlayer().setMedia(media);
        media.release();
        VLCInstance.getMainMediaPlayer().setEqualizer(VLCOptions.getEqualizer());
        VLCInstance.getMainMediaPlayer().setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
        VLCInstance.getMainMediaPlayer().play();
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param position The index of the media
     * @param paused start the media paused
     */
    public void playIndex(Context context, int position, boolean paused) {
        playIndex(context, position, paused ? VLCOptions.MEDIA_PAUSED : 0);
    }

    public void playIndex(Context context, int position) {
        playIndex(context, position, 0);
    }

   /**
    * Expand the current media.
    * @return the index of the media was expanded, and -1 if no media was expanded
    */
    public int expand() {
        final Media media = VLCInstance.getMainMediaPlayer().getMedia();
        final MediaList ml = media.subItems();

        if (ml.getCount() > 0) {
            mMediaList.remove(mPlayerIndex);
            for (int i = 0; i < ml.getCount(); ++i) {
                final Media child = ml.getMediaAt(i);
                child.parse();
                mMediaList.insert(mPlayerIndex, new MediaWrapper(child));
            }
            return 0;
        } else
            return -1;
   }

   public int expand(int index) {
       mPlayerIndex = index;
       return expand();
   }
}
