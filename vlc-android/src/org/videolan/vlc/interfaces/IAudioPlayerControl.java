/*****************************************************************************
 * IAudioPlayer.java
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

package org.videolan.vlc.interfaces;

import org.videolan.vlc.RepeatType;

import android.graphics.Bitmap;

public interface IAudioPlayerControl {
    String getTitle();

    String getTitlePrev();

    String getTitleNext();

    String getArtist();

    String getArtistPrev();

    String getArtistNext();

    String getAlbum();

    Bitmap getCover();

    Bitmap getCoverPrev();

    Bitmap getCoverNext();

    int getLength();

    int getTime();

    boolean hasMedia();

    boolean hasNext();

    boolean hasPrevious();

    void play();

    void pause();

    boolean isPlaying();

    void next();

    void previous();

    void shuffle();

    boolean isShuffling();

    void setRepeatType(RepeatType t);

    RepeatType getRepeatType();

    void detectHeadset(boolean enable);

    float getRate();
}
