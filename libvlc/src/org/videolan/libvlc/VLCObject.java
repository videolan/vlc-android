/*****************************************************************************
 * VLCObject.java
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
import android.os.Looper;

import java.lang.ref.WeakReference;

public abstract class VLCObject {
    private final static String TAG = "LibVLC/VlcObject";

    public static class Events {
        public static final int MediaMetaChanged                  = 0;
        public static final int MediaSubItemAdded               = 1;
        public static final int MediaDurationChanged            = 2;
        public static final int MediaParsedChanged                = 3;
        //public static final int MediaFreed                      = 4;
        public static final int MediaStateChanged               = 5;
        public static final int MediaSubItemTreeAdded           = 6;

        //public static final int MediaPlayerMediaChanged         = 0x100;
        //public static final int MediaPlayerNothingSpecial       = 0x101;
        //public static final int MediaPlayerOpening              = 0x102;
        //public static final int MediaPlayerBuffering            = 0x103;
        //public static final int MediaPlayerPlaying                = 0x104;
        //public static final int MediaPlayerPaused                 = 0x105;
        //public static final int MediaPlayerStopped                = 0x106;
        //public static final int MediaPlayerForward              = 0x107;
        //public static final int MediaPlayerBackward             = 0x108;
        //public static final int MediaPlayerEndReached             = 0x109;
        //public static final int MediaPlayerEncounteredError       = 0x10a;
        //public static final int MediaPlayerTimeChanged            = 0x10b;
        //public static final int MediaPlayerPositionChanged        = 0x10c;
        //public static final int MediaPlayerSeekableChanged      = 0x10d;
        //public static final int MediaPlayerPausableChanged      = 0x10e;
        //public static final int MediaPlayerTitleChanged         = 0x10f;
        //public static final int MediaPlayerSnapshotTaken        = 0x110;
        //public static final int MediaPlayerLengthChanged        = 0x111;
        //public static final int MediaPlayerVout                   = 0x112;

        public static final int MediaListItemAdded              = 0x200;
        //public static final int MediaListWillAddItem            = 0x201;
        public static final int MediaListItemDeleted            = 0x202;
        //public static final int MediaListWillDeleteItem         = 0x203;
        public static final int MediaListEndReached             = 0x204;

        //public static final int MediaListViewItemAdded          = 0x300;
        //public static final int MediaListViewWillAddItem        = 0x301;
        //public static final int MediaListViewItemDeleted        = 0x302;
        //public static final int MediaListViewWillDeleteItem     = 0x303;

        //public static final int MediaListPlayerPlayed           = 0x400;
        //public static final int MediaListPlayerNextItemSet      = 0x401;
        //public static final int MediaListPlayerStopped          = 0x402;

        public static final int MediaDiscovererStarted          = 0x500;
        public static final int MediaDiscovererEnded            = 0x501;

        //public static final int VlmMediaAdded                   = 0x600;
        //public static final int VlmMediaRemoved                 = 0x601;
        //public static final int VlmMediaChanged                 = 0x602;
        //public static final int VlmMediaInstanceStarted         = 0x603;
        //public static final int VlmMediaInstanceStopped         = 0x604;
        //public static final int VlmMediaInstanceStatusInit      = 0x605;
        //public static final int VlmMediaInstanceStatusOpening   = 0x606;
        //public static final int VlmMediaInstanceStatusPlaying   = 0x607;
        //public static final int VlmMediaInstanceStatusPause     = 0x608;
        //public static final int VlmMediaInstanceStatusEnd       = 0x609;
        //public static final int VlmMediaInstanceStatusError     = 0x60a;
    }

    /**
     * Event used by EventListener
     * Can be casted to inherited class Event (like {@link MediaList.Event}).
     */
    public static class Event {
        /**
         * @see Events
         */
        public final int type;
        protected Event(int type) {
            this.type = type;
        }
    }

    /**
     * Listener for libvlc events
     *
     * @see Event
     */
    public interface EventListener {
        public void onEvent(Event event);
    }

    private static class EventRunnable implements Runnable {
        private final EventListener listener;
        private final Event event;

        private EventRunnable(EventListener listener, Event event) {
            this.listener = listener;
            this.event = event;
        }
        @Override
        public void run() {
            listener.onEvent(event);
        }
    }

    private EventListener mEventListener = null;
    private Handler mHandler = null;
    private int mNativeRefCount = 1;

    /**
     * Returns true if native object is released
     */
    public synchronized boolean isReleased() {
        return mNativeRefCount == 0;
    }

    /**
     * Increment internal ref count of the native object.
     * @return true if media is retained
     */
    public synchronized final boolean retain() {
        if (mNativeRefCount > 0) {
            mNativeRefCount++;
            return true;
        } else
            return false;
    }

    /**
     * Release the native object if ref count is 1.
     *
     * After this call, native calls are not possible anymore.
     * You can still call others methods to retrieve cached values.
     * For example: if you parse, then release a media, you'll still be able to retrieve all Metas or Tracks infos.
     */
    public final void release() {
        int refCount = -1;
        synchronized (this) {
            if (mNativeRefCount == 0)
                return;
            if (mNativeRefCount > 0) {
                refCount = --mNativeRefCount;
            }
            // clear event list
            if (refCount == 0)
                setEventListener(null);
        }
        if (refCount == 0) {
            // detach events when not synchronized since onEvent is executed synchronized
            nativeDetachEvents();
            synchronized (this) {
                onReleaseNative();
            }
        }
    }

    /**
     * Set an event listener.
     * Events are sent via the android main thread.
     *
     * @param listener see {@link EventListener}
     */
    public synchronized final void setEventListener(EventListener listener) {
        if (mHandler != null)
            mHandler.removeCallbacksAndMessages(null);
        mEventListener = listener;
        if (mEventListener != null && mHandler == null)
            mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Called when libvlc send events.
     *
     * @param eventType
     * @param arg1
     * @param arg2
     * @return Event that will be dispatched to listeners
     */
    protected abstract Event onEventNative(int eventType, long arg1, long arg2);

    /**
     * Called when native object is released (refcount is 0).
     *
     * This is where you must release native resources.
     */
    protected abstract void onReleaseNative();

    /* JNI */
    private long mInstance = 0; // Read-only, reserved for JNI
    private synchronized void dispatchEventFromNative(int eventType, long arg1, long arg2) {
        if (isReleased())
            return;
        final Event event = onEventNative(eventType, arg1, arg2);
        if (event != null && mEventListener != null && mHandler != null)
            mHandler.post(new EventRunnable(mEventListener, event));
    }
    private final native void nativeDetachEvents();

    /* used only before API 7: substitute for NewWeakGlobalRef */
    private Object getWeakReference() {
        return new WeakReference<VLCObject>(this);
    }
    private static void dispatchEventFromWeakNative(Object weak, int eventType, long arg1, long arg2) {
        VLCObject obj = ((WeakReference<VLCObject>)weak).get();
        if (obj != null)
            obj.dispatchEventFromNative(eventType, arg1, arg2);
    }
}
