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

import java.util.ArrayList;

import org.videolan.libvlc.LibVLC;


public class MediaWrapperListPlayer {

    private int mPlayerIndex = 0;
    final private LibVLC mLibVLC;
    final private MediaWrapperList mMediaList;

    public MediaWrapperListPlayer(LibVLC libVLC) {
        mLibVLC = libVLC;
        mMediaList = new MediaWrapperList(libVLC);
    }

    public MediaWrapperList getMediaList() {
        return mMediaList;
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param position The index of the media
     */
    public void playIndex(int position) {
        String mrl = mMediaList.getMRL(position);
        if (mrl == null)
            return;

        final MediaWrapper media = mMediaList.getMedia(position);
        String[] options = mLibVLC.getMediaOptions(media != null ? media.getFlags() : 0);
        mPlayerIndex = position;
        mLibVLC.playMRL(mrl, options);
    }

    /**
     * Expand and continue playing the current media.
     *
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
   public int expandAndPlay() {
       int r = expand();
       if(r == 0)
           playIndex(mPlayerIndex);
       return r;
   }

   /**
    * Expand the current media.
    * @return the index of the media was expanded, and -1 if no media was expanded
    */
   public int expand() {
       ArrayList<String> children = new ArrayList<String>();
       int ret = mLibVLC.expandMedia(children);
       if(ret == 0) {
           mMediaList.remove(mPlayerIndex);
           for(String mrl : children) {
               mMediaList.insert(mPlayerIndex, mrl);
           }
       }
       return ret;
   }

   public int expand(int index) {
       mPlayerIndex = index;
       return expand();
   }
}
