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

import android.os.Handler;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMediaList;

@SuppressWarnings("unused, JniMissingFunction")
public class MediaList extends VLCObject<IMediaList.Event> implements IMediaList {
    private final static String TAG = "LibVLC/MediaList";

    private int mCount = 0;
    private final SparseArray<IMedia> mMediaArray = new SparseArray<IMedia>();
    private boolean mLocked = false;

    private void init() {
        lock();
        mCount = nativeGetCount();
        for (int i = 0; i < mCount; ++i)
            mMediaArray.put(i, new Media(this, i));
        unlock();
    }

    /**
     * Create a MediaList from libVLC
     *
     * @param ILibVLC a valid libVLC
     */
    public MediaList(ILibVLC ILibVLC) {
        super(ILibVLC);
        nativeNewFromLibVlc(ILibVLC);
        init();
    }

    /**
     * @param md Should not be released
     */
    protected MediaList(MediaDiscoverer md) {
        super(md);
        nativeNewFromMediaDiscoverer(md);
        init();
    }

    /**
     * @param m Should not be released
     */
    protected MediaList(IMedia m) {
        super(m);
        nativeNewFromMedia(m);
        init();
    }

    private synchronized IMedia insertMediaFromEvent(int index) {
        for (int i = mCount - 1; i >= index; --i)
            mMediaArray.put(i + 1, mMediaArray.valueAt(i));
        mCount++;
        final IMedia media = new Media(this, index);
        mMediaArray.put(index, media);
        return media;
    }

    private synchronized IMedia removeMediaFromEvent(int index) {
        mCount--;
        final IMedia media = mMediaArray.get(index);
        if (media != null)
            media.release();
        for (int i = index; i < mCount; ++i) {
            mMediaArray.put(i, mMediaArray.valueAt(i + 1));
        }
        return media;
    }

    public void setEventListener(EventListener listener, Handler handler) {
        super.setEventListener(listener, handler);
    }

    @Override
    protected synchronized Event onEventNative(int eventType, long arg1, long arg2, float argf1, @Nullable String args1) {
        if (mLocked)
            throw new IllegalStateException("already locked from event callback");
        mLocked = true;
        Event event = null;
        int index;

        switch (eventType) {
            case Event.ItemAdded:
                index = (int) arg1;
                if (index != -1) {
                    final IMedia media = insertMediaFromEvent(index);
                    event = new Event(eventType, media, true, index);
                }
                break;
            case Event.ItemDeleted:
                index = (int) arg1;
                if (index != -1) {
                    final IMedia media = removeMediaFromEvent(index);
                    event = new Event(eventType, media, false, index);
                }
                break;
            case Event.EndReached:
                event = new Event(eventType, null, false, -1);
                break;
        }
        mLocked = false;
        return event;
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
     * @param index index of the media
     * @return Media hold by MediaList. This Media should be released with {@link #release()}.
     */
    public synchronized IMedia getMediaAt(int index) {
        if (index < 0 || index >= getCount())
            throw new IndexOutOfBoundsException();
        final IMedia media = mMediaArray.get(index);
        media.retain();
        return media;
    }

    @Override
    public void onReleaseNative() {
        for (int i = 0; i < mMediaArray.size(); ++i) {
            final IMedia media = mMediaArray.get(i);
            if (media != null)
                media.release();
        }

        nativeRelease();
    }

    private synchronized void lock() {
        if (mLocked)
            throw new IllegalStateException("already locked");
        mLocked = true;
        nativeLock();
    }

    private synchronized void unlock() {
        if (!mLocked)
            throw new IllegalStateException("not locked");
        mLocked = false;
        nativeUnlock();
    }

    public synchronized boolean isLocked() {
        return mLocked;
    }

    /* JNI */
    private native void nativeNewFromLibVlc(ILibVLC libvlc);

    private native void nativeNewFromMediaDiscoverer(MediaDiscoverer md);

    private native void nativeNewFromMedia(IMedia m);

    private native void nativeRelease();

    private native int nativeGetCount();

    private native void nativeLock();

    private native void nativeUnlock();
}
