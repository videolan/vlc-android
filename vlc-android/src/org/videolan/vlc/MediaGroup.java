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

import org.videolan.libvlc.Media;

public class MediaGroup extends Media {

    public final static String TAG = "VLC/MediaGroup";

    public final static int MIN_GROUP_LENGTH = 5;

    private Media mMedia;
    private ArrayList<Media> mMedias;

    public MediaGroup(Media media)
    {
        super(media.getLocation(),
                media.getTime(),
                media.getLength(),
                Media.TYPE_GROUP,
                Util.getPictureFromCache(media),
                media.getTitle(),
                media.getArtist(),
                media.getGenre(),
                media.getAlbum(),
                media.getWidth(),
                media.getHeight(),
                media.getArtworkURL(),
                media.getAudioTrack(),
                media.getSpuTrack());
        mMedia = media;
        mMedias = new ArrayList<Media>();
    }

    public void add(Media media) {
        mMedias.add(media);
    }

    public Media getMedia() {
        return size() == 0 ? mMedia : this;
    }

    public Media getFirstMedia() {
        return size() == 0 ? mMedia : mMedias.get(0);
    }

    public int size() {
        return mMedias.size();
    }

    public void merge(Media media, String title) {
        if (size() == 0) {
            if (mMedia != null)
                mMedias.add(mMedia);
            mMedia = null;
        }
        mMedias.add(media);
        this.mTitle = title;
    }

    public static List<MediaGroup> group(List<Media> mediaList) {
        ArrayList<MediaGroup> groups = new ArrayList<MediaGroup>();
        for (Media media : mediaList)
            insertInto(groups, media);
        return groups;
    }

    private static void insertInto(ArrayList<MediaGroup> groups, Media media)
    {
        for (MediaGroup mediaGroup : groups) {
            CharSequence group = mediaGroup.getTitle();
            CharSequence item = media.getTitle();

            // find common prefix
            int commonLength = 0;
            int minLength = Math.min(group.length(), item.length());
            while (commonLength < minLength && group.charAt(commonLength) == item.charAt(commonLength))
                ++commonLength;

            // same prefix name, just add
            if (commonLength == group.length() && mediaGroup.size() > 0)
                mediaGroup.add(media);
            // not the same prefix, but close : merge
            else if (commonLength > 0 && (commonLength < group.length() || mediaGroup.size() == 0) && commonLength > MIN_GROUP_LENGTH)
                mediaGroup.merge(media, group.subSequence(0, commonLength).toString());
            else
                continue;

            return;
        }

        // does not match any group, so add one
        groups.add(new MediaGroup(media));
    }
}
