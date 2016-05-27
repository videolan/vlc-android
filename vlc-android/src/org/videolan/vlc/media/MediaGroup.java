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

package org.videolan.vlc.media;

import org.videolan.vlc.gui.helpers.BitmapUtil;

import java.util.ArrayList;
import java.util.List;

public class MediaGroup extends MediaWrapper {

    public final static String TAG = "VLC/MediaGroup";

    public final static int MIN_GROUP_LENGTH = 6;

    private ArrayList<MediaWrapper> mMedias;

    public MediaGroup(MediaWrapper media) {
        super(media.getUri(),
                media.getTime(),
                media.getLength(),
                MediaWrapper.TYPE_GROUP,
                BitmapUtil.getPicture(media),
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

    public String getDisplayTitle() {
        return getTitle() + "\u2026";
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

    public ArrayList<MediaWrapper> getAll() {
        return mMedias;
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

    private static void insertInto(ArrayList<MediaGroup> groups, MediaWrapper media) {
        for (MediaGroup mediaGroup : groups) {
            String group = mediaGroup.getTitle();
            String title = media.getTitle();

            //Handle titles starting with "The"
            int groupOffset = group.toLowerCase().startsWith("the") ? 4 : 0;
            if (title.toLowerCase().startsWith("the"))
                title = title.substring(4);

            // find common prefix
            int commonLength = 0;
            String groupTitle = group.substring(groupOffset);
            int minLength = Math.min(groupTitle.length(), title.length());
            while (commonLength < minLength && groupTitle.charAt(commonLength) == title.charAt(commonLength))
                ++commonLength;

            if (commonLength >= MIN_GROUP_LENGTH) {
                if (commonLength == group.length()) {
                    // same prefix name, just add
                    mediaGroup.add(media);
                } else {
                    // not the same prefix, but close : merge
                    mediaGroup.merge(media, group.substring(0, commonLength+groupOffset));
                }
                return;
            }
        }

        // does not match any group, so add one
        groups.add(new MediaGroup(media));
    }
}
