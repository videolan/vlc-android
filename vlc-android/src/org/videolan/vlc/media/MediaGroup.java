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

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.gui.helpers.BitmapUtil;

import java.util.ArrayList;
import java.util.List;

public class MediaGroup extends MediaWrapper {

    public final static String TAG = "VLC/MediaGroup";

    private List<MediaWrapper> mMedias;

    private MediaGroup(MediaWrapper media, boolean filename) {
        super(media.getUri(),
                media.getTime(),
                media.getLength(),
                MediaWrapper.TYPE_GROUP,
                BitmapUtil.getPicture(media),
                filename ? media.getFileName() : media.getTitle(),
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
                media.getLastModified(),
                media.getSeen());
        mMedias = new ArrayList<>();
        mMedias.add(media);
    }

    public String getDisplayTitle() {
        return getTitle() + "\u2026";
    }

    @Override
    public String getFileName() {
        return mTitle;
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

    public List<MediaWrapper> getAll() {
        return mMedias;
    }

    public int size() {
        return mMedias.size();
    }

    private void merge(MediaWrapper media, String title) {
        mMedias.add(media);
        this.mTitle = title;
    }

    public static List<MediaGroup> group(MediaWrapper[] mediaList, int minGroupLengthValue, boolean filename) {
        final ArrayList<MediaGroup> groups = new ArrayList<>();
        for (MediaWrapper media : mediaList) if (media != null) insertInto(groups, media, minGroupLengthValue, filename);
        return groups;
    }

    private static void insertInto(ArrayList<MediaGroup> groups, MediaWrapper media, int minGroupLengthValue, boolean filename) {
        for (MediaGroup mediaGroup : groups) {
            final String group = mediaGroup.getTitle().toLowerCase();
            String title = (filename ? media.getFileName() : media.getTitle()).toLowerCase();

            //Handle titles starting with "The"
            int groupOffset = group.startsWith("the") ? 4 : 0;
            if (title.startsWith("the"))
                title = title.substring(4);

            // find common prefix
            int commonLength = 0;
            final String groupTitle = group.substring(groupOffset);
            final int minLength = Math.min(groupTitle.length(), title.length());
            while (commonLength < minLength
                    && groupTitle.charAt(commonLength) == title.charAt(commonLength))
                ++commonLength;

            if (commonLength >= minGroupLengthValue && minGroupLengthValue != 0) {
                if (commonLength == group.length()) {
                    // same prefix name, just add
                    mediaGroup.add(media);
                } else {
                    // not the same prefix, but close : merge
                    mediaGroup.merge(media, mediaGroup.getTitle().substring(0, commonLength+groupOffset));
                }
                return;
            }
        }

        // does not match any group, so add one
        groups.add(new MediaGroup(media, filename));
    }
}
