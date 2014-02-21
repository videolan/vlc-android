/*****************************************************************************
 * IAudioService.aidl
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

package org.videolan.vlc.interfaces;
import org.videolan.vlc.interfaces.IAudioServiceCallback;

interface IAudioService {
    void play();
    void pause();
    void stop();
    void next();
    void previous();
    void shuffle();
    void setTime(long time);
    void load(in List<String> mediaPathList, int position, boolean noVideo);
    void append(in List<String> mediaPathList);
    void moveItem(int positionStart, int positionEnd);
    void remove(int position);
    void removeLocation(String location);
    List<String> getMediaLocations();
    String getCurrentMediaLocation();
    boolean isPlaying();
    boolean isShuffling();
    int getRepeatType();
    void setRepeatType(int t);
    boolean hasMedia();
    boolean hasNext();
    boolean hasPrevious();
    String getTitle();
    String getTitlePrev();
    String getTitleNext();
    String getArtist();
    String getArtistPrev();
    String getArtistNext();
    String getAlbum();
    int getTime();
    int getLength();
    Bitmap getCover();
    Bitmap getCoverPrev();
    Bitmap getCoverNext();
    void addAudioCallback(IAudioServiceCallback cb);
    void removeAudioCallback(IAudioServiceCallback cb);
    void detectHeadset(boolean enable);
    void showWithoutParse(int index);
    void playIndex(int index);
    float getRate();
}
