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

@SuppressWarnings("JniMissingFunction")
abstract class VLCObject<T extends VLCEvent> {
    private VLCEvent.Listener<T> mEventListener = null;
    private Handler mHandler = null;
    final LibVLC mLibVLC;
    private int mNativeRefCount = 1;

    protected VLCObject(LibVLC libvlc) {
        mLibVLC = libvlc;
    }

    protected VLCObject(VLCObject parent) {
        mLibVLC = parent.mLibVLC;
    }

    protected VLCObject() {
        mLibVLC = null;
    }

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
     * @param listener see {@link VLCEvent.Listener}
     */
    protected synchronized void setEventListener(VLCEvent.Listener<T> listener) {
        setEventListener(listener, null);
    }

    /**
     * Set an event listener and an executor Handler
     * @param listener see {@link VLCEvent.Listener}
     * @param handler Handler in which events are sent. If null, a handler will be created running on the main thread
     */
    protected synchronized void setEventListener(VLCEvent.Listener<T> listener, Handler handler) {
        if (mHandler != null)
            mHandler.removeCallbacksAndMessages(null);
        mEventListener = listener;
        if (mEventListener == null)
            mHandler = null;
        else if (mHandler == null)
            mHandler = handler != null ? handler : new Handler(Looper.getMainLooper());
    }

    /**
     * Called when libvlc send events.
     *
     * @param eventType event type
     * @param arg1 first argument
     * @param arg2 second argument
     * @param argf1 first float argument
     * @return Event that will be dispatched to listeners
     */
    protected abstract T onEventNative(int eventType, long arg1, long arg2, float argf1);

    /**
     * Called when native object is released (refcount is 0).
     *
     * This is where you must release native resources.
     */
    protected abstract void onReleaseNative();

    /* JNI */
    @SuppressWarnings("unused") /* Used from JNI */
    private long mInstance = 0;
    private synchronized void dispatchEventFromNative(int eventType, long arg1, long arg2, float argf1) {
        if (isReleased())
            return;
        final T event = onEventNative(eventType, arg1, arg2, argf1);

        class EventRunnable implements Runnable {
            private final VLCEvent.Listener<T> listener;
            private final T event;

            private EventRunnable(VLCEvent.Listener<T> listener, T event) {
                this.listener = listener;
                this.event = event;
            }
            @Override
            public void run() {
                listener.onEvent(event);
            }
        }

        if (event != null && mEventListener != null && mHandler != null)
            mHandler.post(new EventRunnable(mEventListener, event));
    }
    private native void nativeDetachEvents();

    /* used only before API 7: substitute for NewWeakGlobalRef */
    @SuppressWarnings("unused") /* Used from JNI */
    private Object getWeakReference() {
        return new WeakReference<VLCObject>(this);
    }
    @SuppressWarnings("unchecked,unused") /* Used from JNI */
    private static void dispatchEventFromWeakNative(Object weak, int eventType, long arg1, long arg2,
                                                    float argf1) {
        VLCObject obj = ((WeakReference<VLCObject>)weak).get();
        if (obj != null)
            obj.dispatchEventFromNative(eventType, arg1, arg2, argf1);
    }
}
