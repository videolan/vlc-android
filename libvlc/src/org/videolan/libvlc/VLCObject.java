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

import androidx.annotation.Nullable;

import org.videolan.libvlc.interfaces.AbstractVLCEvent;
import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.IVLCObject;

@SuppressWarnings("JniMissingFunction")
abstract class VLCObject<T extends AbstractVLCEvent> implements IVLCObject<T> {
    private AbstractVLCEvent.Listener<T> mEventListener = null;
    private Handler mHandler = null;
    final ILibVLC mILibVLC;
    private int mNativeRefCount = 1;

    protected VLCObject(ILibVLC libvlc) {
        mILibVLC = libvlc;
    }

    protected VLCObject(IVLCObject parent) {
        mILibVLC = parent.getLibVLC();
    }

    protected VLCObject() {
        mILibVLC = null;
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

    @Override
    protected synchronized void finalize() {
        if (!isReleased())
            throw new AssertionError("VLCObject (" + getClass().getName() + ") finalized but not natively released (" + mNativeRefCount + " refs)");
    }

    @Override
    public ILibVLC getLibVLC() {
        return mILibVLC;
    }

    /**
     * Set an event listener.
     * Events are sent via the android main thread.
     *
     * @param listener see {@link AbstractVLCEvent.Listener}
     */
    protected synchronized void setEventListener(AbstractVLCEvent.Listener<T> listener) {
        setEventListener(listener, null);
    }

    /**
     * Set an event listener and an executor Handler
     * @param listener see {@link AbstractVLCEvent.Listener}
     * @param handler Handler in which events are sent. If null, a handler will be created running on the main thread
     */
    protected synchronized void setEventListener(AbstractVLCEvent.Listener<T> listener, Handler handler) {
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
    protected abstract T onEventNative(int eventType, long arg1, long arg2, float argf1, String args1);

    /**
     * Called when native object is released (refcount is 0).
     *
     * This is where you must release native resources.
     */
    protected abstract void onReleaseNative();

    /* JNI */
    @SuppressWarnings("unused") /* Used from JNI */
    private long mInstance = 0;
    private synchronized void dispatchEventFromNative(int eventType, long arg1, long arg2, float argf1, @Nullable String args1) {
        if (isReleased())
            return;
        final T event = onEventNative(eventType, arg1, arg2, argf1, args1);

        class EventRunnable implements Runnable {
            private final AbstractVLCEvent.Listener<T> listener;
            private final T event;

            private EventRunnable(AbstractVLCEvent.Listener<T> listener, T event) {
                this.listener = listener;
                this.event = event;
            }
            @Override
            public void run() {
                listener.onEvent(event);
                event.release();
            }
        }

        if (event != null && mEventListener != null && mHandler != null)
            mHandler.post(new EventRunnable(mEventListener, event));
    }
    private native void nativeDetachEvents();

    public native long getInstance();
}
