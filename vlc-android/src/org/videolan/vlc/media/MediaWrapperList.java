/*****************************************************************************
 * MediaWrapperList.java
 *****************************************************************************
 * Copyright © 2013-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.media;

import android.net.Uri;
import android.support.annotation.Nullable;

import java.util.ArrayList;

public class MediaWrapperList {
    private static final String TAG = "VLC/MediaWrapperList";

    public interface EventListener {
        void onItemAdded(int index, String mrl);
        void onItemRemoved(int index, String mrl);
        void onItemMoved(int indexBefore, int indexAfter, String mrl);
    }

    private static final int EVENT_ADDED = 0;
    private static final int EVENT_REMOVED = 1;
    private static final int EVENT_MOVED = 2;

    /* TODO: add locking */
    private ArrayList<MediaWrapper> mInternalList;
    private ArrayList<EventListener> mEventListenerList;

    public MediaWrapperList() {
        mEventListenerList = new ArrayList<EventListener>();
        mInternalList = new ArrayList<MediaWrapper>();
    }

    public void add(MediaWrapper media) {
        mInternalList.add(media);
    }

    public synchronized void addEventListener(EventListener listener) {
        if (!mEventListenerList.contains(listener))
            mEventListenerList.add(listener);
    }

    public synchronized void removeEventListener(EventListener listener) {
        mEventListenerList.remove(listener);
    }

    private synchronized void signalEventListeners(int event, int arg1, int arg2, String mrl) {
        for (EventListener listener : mEventListenerList) {
            switch (event) {
            case EVENT_ADDED:
                listener.onItemAdded(arg1, mrl);
                break;
            case EVENT_REMOVED:
                listener.onItemRemoved(arg1, mrl);
                break;
            case EVENT_MOVED:
                listener.onItemMoved(arg1, arg2, mrl);
                break;
            }
        }
    }

    /**
     * Clear the media list. (remove all media)
     */
    public void clear() {
        // Signal to observers of media being deleted.
        for(int i = 0; i < mInternalList.size(); i++)
            signalEventListeners(EVENT_REMOVED, i, -1, mInternalList.get(i).getLocation());
        mInternalList.clear();
    }

    private boolean isValid(int position) {
        return position >= 0 && position < mInternalList.size();
    }

    public void insert(int position, Uri uri) {
        insert(position, new MediaWrapper(uri));
    }
    public void insert(int position, MediaWrapper media) {
        mInternalList.add(position, media);
        signalEventListeners(EVENT_ADDED, position, -1, media.getLocation());
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

        MediaWrapper toMove = mInternalList.get(startPosition);
        mInternalList.remove(startPosition);
        if (startPosition >= endPosition)
            mInternalList.add(endPosition, toMove);
        else
            mInternalList.add(endPosition - 1, toMove);
        signalEventListeners(EVENT_MOVED, startPosition, endPosition, toMove.getLocation());
    }

    public void remove(int position) {
        if (!isValid(position))
            return;
        String uri = mInternalList.get(position).getLocation();
        mInternalList.remove(position);
        signalEventListeners(EVENT_REMOVED, position, -1, uri);
    }

    public void remove(String location) {
        for (int i = 0; i < mInternalList.size(); ++i) {
            String uri = mInternalList.get(i).getLocation();
            if (uri.equals(location)) {
                mInternalList.remove(i);
                signalEventListeners(EVENT_REMOVED, i, -1, uri);
                i--;
            }
        }
    }

    public int size() {
        return mInternalList.size();
    }

    @Nullable
    public MediaWrapper getMedia(int position) {
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
}
