/*****************************************************************************
 * MediaGroup.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

package org.videolan.vlc;

import java.util.ArrayList;
import java.util.List;

import org.videolan.vlc.util.BitmapUtil;

public class MediaGroup extends MediaWrapper {

    public final static String TAG = "VLC/MediaGroup";

    public final static int MIN_GROUP_LENGTH = 6;

    private ArrayList<MediaWrapper> mMedias;

    public MediaGroup(MediaWrapper media)
    {
        super(media.getUri(),
                media.getTime(),
                media.getLength(),
                MediaWrapper.TYPE_GROUP,
                BitmapUtil.getPictureFromCache(media),
                media.getTitle(),
                media.getArtist(),
                media.getGenre(),
                media.getAlbum(),
                media.getAlbumArtist(),
                media.getWidth(),
                media.getHeight(),
                media.getArtworkURL(),
                media.getAudioTrack(),
                media.getSpuTrack(),
                media.getTrackNumber(),
                media.getDiscNumber(),
                0l);
        mMedias = new ArrayList<MediaWrapper>();
        mMedias.add(media);
    }

    public void add(MediaWrapper media) {
        mMedias.add(media);
    }

    public MediaWrapper getMedia() {
        return mMedias.size() == 1 ? mMedias.get(0) : this;
    }

    public MediaWrapper getFirstMedia() {
        return mMedias.get(0);
    }

    public int size() {
        return mMedias.size();
    }

    public void merge(MediaWrapper media, String title) {
        mMedias.add(media);
        this.mTitle = title;
    }

    public static List<MediaGroup> group(List<MediaWrapper> mediaList) {
        ArrayList<MediaGroup> groups = new ArrayList<MediaGroup>();
        for (MediaWrapper media : mediaList)
            insertInto(groups, media);
        return groups;
    }

    private static void insertInto(ArrayList<MediaGroup> groups, MediaWrapper media)
    {
        for (MediaGroup mediaGroup : groups) {
            String group = mediaGroup.getTitle();
            String item = media.getTitle();

            // find common prefix
            int commonLength = 0;
            int minLength = Math.min(group.length(), item.length());
            while (commonLength < minLength && group.charAt(commonLength) == item.charAt(commonLength))
                ++commonLength;

            if (commonLength >= MIN_GROUP_LENGTH) {
                if (commonLength == group.length()) {
                    // same prefix name, just add
                    mediaGroup.add(media);
                } else {
                    // not the same prefix, but close : merge
                    mediaGroup.merge(media, group.substring(0, commonLength));
                }
                return;
            }
        }

        // does not match any group, so add one
        groups.add(new MediaGroup(media));
    }
}
