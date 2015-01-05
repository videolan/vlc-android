/*****************************************************************************
 * MediaList.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
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
package org.videolan.libvlc;

import java.util.ArrayList;

import android.os.Bundle;

/**
 * Java/JNI wrapper for the libvlc_media_list_t structure.
 */
public class MediaList {
    private static final String TAG = "VLC/LibVLC/MediaList";


    /* TODO: add locking */
    private ArrayList<Media> mInternalList;
    private LibVLC mLibVLC; // Used to create new objects that require a libvlc instance
    private EventHandler mEventHandler;

    public MediaList(LibVLC libVLC) {
        mEventHandler = new EventHandler(); // used in init() below to fire events at the correct targets
        mInternalList = new ArrayList<Media>();
        mLibVLC = libVLC;
    }

    public void add(Media media) {
        mInternalList.add(media);
    }

    /**
     * Clear the media list. (remove all media)
     */
    public void clear() {
        // Signal to observers of media being deleted.
        for(int i = 0; i < mInternalList.size(); i++) {
            signal_list_event(EventHandler.CustomMediaListItemDeleted, i, mInternalList.get(i).getLocation());
        }
        mInternalList.clear();
    }

    private boolean isValid(int position) {
        return position >= 0 && position < mInternalList.size();
    }

    public void insert(int position, String mrl) {
        insert(position, new Media(mLibVLC, mrl));
    }
    public void insert(int position, Media media) {
        mInternalList.add(position, media);
        signal_list_event(EventHandler.CustomMediaListItemAdded, position, media.getLocation());
    }

    /**
     * Move a media from one position to another
     *
     * @param startPosition start position
     * @param endPosition end position
     * @throws IndexOutOfBoundsException
     */
    public void move(int startPosition, int endPosition) {
        if (!(isValid(startPosition)
              && endPosition >= 0 && endPosition <= mInternalList.size()))
            throw new IndexOutOfBoundsException("Indexes out of range");

        Media toMove = mInternalList.get(startPosition);
        mInternalList.remove(startPosition);
        if (startPosition >= endPosition)
            mInternalList.add(endPosition, toMove);
        else
            mInternalList.add(endPosition - 1, toMove);
        Bundle b = new Bundle();
        b.putInt("index_before", startPosition);
        b.putInt("index_after", endPosition);
        mEventHandler.callback(EventHandler.CustomMediaListItemMoved, b);
    }

    public void remove(int position) {
        if (!isValid(position))
            return;
        String uri = mInternalList.get(position).getLocation();
        mInternalList.remove(position);
        signal_list_event(EventHandler.CustomMediaListItemDeleted, position, uri);
    }

    public void remove(String location) {
        for (int i = 0; i < mInternalList.size(); ++i) {
            String uri = mInternalList.get(i).getLocation();
            if (uri.equals(location)) {
                mInternalList.remove(i);
                signal_list_event(EventHandler.CustomMediaListItemDeleted, i, uri);
                i--;
            }
        }
    }

    public int size() {
        return mInternalList.size();
    }

    public Media getMedia(int position) {
        if (!isValid(position))
            return null;
        return mInternalList.get(position);
    }

    /**
     * @param position The index of the media in the list
     * @return null if not found
     */
    public String getMRL(int position) {
        if (!isValid(position))
            return null;
        return mInternalList.get(position).getLocation();
    }

    public EventHandler getEventHandler() {
        return mEventHandler;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LibVLC Media List: {");
        for(int i = 0; i < size(); i++) {
            sb.append(((Integer)i).toString());
            sb.append(": ");
            sb.append(getMRL(i));
            sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    private void signal_list_event(int event, int position, String uri) {
        Bundle b = new Bundle();
        b.putString("item_uri", uri);
        b.putInt("item_index", position);
        mEventHandler.callback(event, b);
    }
}
