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

public final class MediaDiscoverer extends VLCObject {
    private final static String TAG = "LibVLC/MediaDiscoverer";
    private MediaList mMediaList;

    /**
     * Create a MediaDiscover.
     *
     * @param libVLC
     * @param name Name of the vlc service discovery ("dsm", "upnp", "bonjour"...).
     */
    public MediaDiscoverer(LibVLC libVLC, String name) {
        nativeNew(libVLC, name);
    }

    /**
     * Starts the discovery.
     *
     * @return true the serive is started
     */
    public boolean start() {
        if (!isReleased())
            return nativeStart();
        else
            return false;
    }

    /**
     * Stops the discovery.
     * (You can also call {@link #release() to stop the discovery directly}.
     */
    public void stop() {
        if (!isReleased())
            nativeStop();
    }

    @Override
    protected Event onEventNative(int event, long arg1, long arg2) {
        return null;
    }

    /**
     * Get the MediaList associated with the MediaDiscoverer.
     *
     * @return MediaList, Should NOT be released.
     */
    public synchronized MediaList getMediaList() {
        if (mMediaList == null && !isReleased())
            mMediaList = new MediaList(this);
        return mMediaList;
    }

    @Override
    protected void onReleaseNative() {
        if (mMediaList != null)
            mMediaList.release();
        nativeRelease();
    }

    /* JNI */
    private long mInstance = 0; // Read-only, reserved for JNI
    private native void nativeNew(LibVLC libVLC, String name);
    private native void nativeRelease();
    private native boolean nativeStart();
    private native void nativeStop();
}
