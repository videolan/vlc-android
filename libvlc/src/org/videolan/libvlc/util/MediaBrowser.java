/*****************************************************************************
 * MediaBrowser.java
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

package org.videolan.libvlc.util;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaDiscoverer;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.VLCObject;

public class MediaBrowser {
    private static final String TAG = "LibVLC/util/MediaBrowser";

    private LibVLC mLibVlc;
    private MediaDiscoverer mMediaDiscoverer;
    private MediaList mMediaList;
    private Media mMedia;
    private EventListener mEventListener;

    /**
     * Listener called when medias are added or removed.
     */
    public interface EventListener {
        public void onMediaAdded(int index, Media media);
        public void onMediaRemoved(int index);
        /**
         * Called when browse ended.
         * It won't be called when you browse a service discovery.
         */
        public void onBrowseEnd();
    }

    public MediaBrowser(LibVLC libvlc, EventListener listener) {
        mLibVlc = libvlc; // XXX mLibVlc.retain();
        mEventListener = listener;
    }

    private synchronized void reset() {
        if (mMediaDiscoverer != null) {
            mMediaDiscoverer.release();
            mMediaDiscoverer = null;
        }
        if (mMedia != null) {
            mMedia.release();
            mMedia = null;
        }
        /* don't need to release the MediaList since it's either
         * associated with a Media or a MediaDiscoverer that will release it */
        mMediaList = null;
    }

    /**
     * Release the MediaBrowser.
     */
    public synchronized void release() {
        reset();
    }

    /**
     * Browse to the specified mrl.
     *
     * @param mrl
     */
    public synchronized void browse(String mrl) {
        if (!mrl.contains("://") && !mrl.startsWith("/")) {
            reset();
            if (mrl.equals("smb"))
                mMediaDiscoverer = new MediaDiscoverer(mLibVlc, "dsm");
            else
                mMediaDiscoverer = new MediaDiscoverer(mLibVlc, mrl);
            mMediaList = mMediaDiscoverer.getMediaList();
            mMediaList.setEventListener(mMediaListEventListener);
            mMediaDiscoverer.start();
        } else {
            final Media media = new Media(mLibVlc, mrl);
            browse(media);
            media.release();
        }
    }

    /**
     * Browse to the specified media.
     *
     * @param media Can be a media returned by MediaBrowser.
     */
    public synchronized void browse(Media media) {
        /* media can be associated with a medialist,
         * so increment ref count in order to don't clean it with the medialist
         */
        media.retain();
        reset();
        mMediaList = media.subItems();
        mMediaList.setEventListener(mMediaListEventListener);
        media.parseAsync(Media.Parse.ParseNetwork);
        mMedia = media;
    }

    /**
     * Get the number or media.
     */
    public synchronized int getMediaCount() {
        return mMediaList != null ? mMediaList.getCount() : 0;
    }

    /**
     * Get a media at a specified index.
     */
    public synchronized Media getMediaAt(int index) {
        return mMediaList != null ? mMediaList.getMediaAt(index) : null;
    }

    private MediaList.EventListener mMediaListEventListener = new MediaList.EventListener() {
        @Override
        public void onEvent(VLCObject.Event event) {
            if (mEventListener == null)
                return;
            final MediaList.Event mlEvent = (MediaList.Event) event;

            switch (mlEvent.type) {
            case MediaList.Events.MediaListItemAdded:
                mEventListener.onMediaAdded(mlEvent.index, mlEvent.media);
                break;
            case MediaList.Events.MediaListItemDeleted:
                mEventListener.onMediaRemoved(mlEvent.index);
                break;
            case MediaList.Events.MediaListEndReached:
                mEventListener.onBrowseEnd();
            }
        }
    };
}
