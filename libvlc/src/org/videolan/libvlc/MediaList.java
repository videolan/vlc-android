/*****************************************************************************
 * MediaList.java
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
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

import android.util.SparseArray;

public final class MediaList extends VLCObject {
    private final static String TAG = "LibVLC/MediaList";

    public static class Event extends VLCObject.Event {
        public final Media media;
        public final int index;

        protected Event(int type, Media media, int index) {
            super(type);
            this.media = media;
            this.index = index;
        }
    }

    private int mCount = 0;
    private SparseArray<Media> mMediaArray = new SparseArray<Media>();

    private void init() {
        mCount = nativeGetCount();
    }

    /**
     * Create a MediaList from libVLC
     * @param libVLC
     */
    public MediaList(LibVLC libVLC) {
        nativeNewFromLibVlc(libVLC);
        init();
    }

    /**
     *
     * @param md Should not be released
     */
    protected MediaList(MediaDiscoverer md) {
        if (md.isReleased())
            throw new IllegalArgumentException("MediaDiscoverer is not native");
        nativeNewFromMediaDiscoverer(md);
        init();
    }

    /**
     *
     * @param m Should not be released
     */
    protected MediaList(Media m) {
        if (m.isReleased())
            throw new IllegalArgumentException("Media is not native");
        nativeNewFromMedia(m);
        init();
    }

    private synchronized void insertMedia(int index) {
        mCount++;

        for (int i = mCount - 1; i >= index; --i)
            mMediaArray.put(i + 1, mMediaArray.valueAt(i));
        mMediaArray.put(index, new Media(this, index));
    }

    private synchronized void removeMedia(int index) {
        mCount--;
        Media media = mMediaArray.get(index);
        if (media != null)
            media.release();
        for (int i = index; i < mCount; ++i) {
            mMediaArray.put(i, mMediaArray.valueAt(i + 1));
        }
    }

    @Override
    protected synchronized Event onEventNative(int eventType, long arg1, long arg2) {
        int index = -1;
        switch (eventType) {
        case Events.MediaListItemAdded:
            index = (int) arg1;
            if (index != -1) {
                insertMedia(index);
                return new Event(eventType, mMediaArray.get(index), index);
            } else
                return null;
        case Events.MediaListItemDeleted:
            index = (int) arg1;
            if (index != -1) {
                removeMedia(index);
                return new Event(eventType, null, index);
            } else
                return null;
        case Events.MediaListEndReached:
            return new Event(eventType, null, -1);
        }
        return null;
    }

    /**
     * Get the number of Media.
     */
    public synchronized int getCount() {
        return mCount;
    }

    /**
     * Get a Media at specified index.
     *
     * @param index
     * @return Media hold by MediaList, Should NOT be released.
     */
    public synchronized Media getMediaAt(int index) {
        if (index < 0 || index > getCount())
            return null;
        return mMediaArray.get(index);
    }

    @Override
    public void onReleaseNative() {
        for (int i = 0; i < mMediaArray.size(); ++i) {
            final Media media = mMediaArray.get(i);
            if (media != null)
                media.release();
        }

        nativeRelease();
    }

    /* JNI */
    private native void nativeNewFromLibVlc(LibVLC libvlc);
    private native void nativeNewFromMediaDiscoverer(MediaDiscoverer md);
    private native void nativeNewFromMedia(Media m);
    private native void nativeRelease();
    private native int nativeGetCount();
}
