/*****************************************************************************
 * MediaDiscoverer.java
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

@SuppressWarnings("unused")
public class MediaDiscoverer extends VLCObject<MediaDiscoverer.Event> {
    private final static String TAG = "LibVLC/MediaDiscoverer";

    public static class Event extends VLCEvent {

        public static final int Started = 0x500;
        public static final int Ended   = 0x501;

        protected Event(int type) {
            super(type);
        }
    }

    public interface EventListener extends VLCEvent.Listener<MediaDiscoverer.Event> {}

    private MediaList mMediaList = null;

    /**
     * Create a MediaDiscover.
     *
     * @param libVLC a valid LibVLC
     * @param name Name of the vlc service discovery ("dsm", "upnp", "bonjour"...).
     */
    public MediaDiscoverer(LibVLC libVLC, String name) {
        nativeNew(libVLC, name);
    }

    /**
     * Starts the discovery. This MediaDiscoverer should be alive (not released).
     *
     * @return true the service is started
     */
    public boolean start() {
        if (isReleased())
            throw new IllegalStateException("MediaDiscoverer is released");
        return nativeStart();
    }

    /**
     * Stops the discovery. This MediaDiscoverer should be alive (not released).
     * (You can also call {@link #release() to stop the discovery directly}.
     */
    public void stop() {
        if (isReleased())
            throw new IllegalStateException("MediaDiscoverer is released");
        nativeStop();
    }

    public void setEventListener(EventListener listener) {
        super.setEventListener(listener);
    }

    @Override
    protected Event onEventNative(int eventType, long arg1, float arg2) {
        switch (eventType) {
            case Event.Started:
            case Event.Ended:
                return new Event(eventType);
        }
        return null;
    }

    /**
     * Get the MediaList associated with the MediaDiscoverer.
     * This MediaDiscoverer should be alive (not released).
     *
     * @return MediaList. This MediaList should be released with {@link #release()}.
     */
    public MediaList getMediaList() {
        synchronized (this) {
            if (mMediaList != null) {
                mMediaList.retain();
                return mMediaList;
            }
        }
        final MediaList mediaList = new MediaList(this);
        synchronized (this) {
            mMediaList = mediaList;
            mMediaList.retain();
            return mMediaList;
        }
    }

    @Override
    protected void onReleaseNative() {
        if (mMediaList != null)
            mMediaList.release();
        nativeRelease();
    }

    /* JNI */
    private native void nativeNew(LibVLC libVLC, String name);
    private native void nativeRelease();
    private native boolean nativeStart();
    private native void nativeStop();
}
