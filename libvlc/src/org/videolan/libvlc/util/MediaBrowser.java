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

import java.util.ArrayList;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaDiscoverer;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.VLCObject;

public class MediaBrowser {
    private static final String TAG = "LibVLC/util/MediaBrowser";

    private static final String[] DISCOVERER_LIST = {
        "dsm", // Netbios discovery via libdsm
        "upnp",
        // "bonjour",
        //  "mdns"
    };

    private LibVLC mLibVlc;
    private ArrayList<MediaDiscoverer> mMediaDiscoverers = new ArrayList<MediaDiscoverer>();
    private ArrayList<Media> mDiscovererMediaArray = new ArrayList<Media>();
    private MediaList mBrowserMediaList;
    private Media mMedia;
    private EventListener mEventListener;

    /**
     * Listener called when medias are added or removed.
     */
    public interface EventListener {
        /**
         * Received when a new media is added.
         * @param index
         * @param media
         */
        public void onMediaAdded(int index, Media media);
        /**
         * Received when a media is removed (Happens only when you discover networks)
         * @param index
         * @param media Released media, but cached attributes are still
         * available (like media.getMrl())
         */
        public void onMediaRemoved(int index, Media media);
        /**
         * Called when browse ended.
         * It won't be called when you discover networks
         */
        public void onBrowseEnd();
    }

    public MediaBrowser(LibVLC libvlc, EventListener listener) {
        mLibVlc = libvlc; // XXX mLibVlc.retain();
        mEventListener = listener;
    }

    private synchronized void reset() {
        for (MediaDiscoverer md : mMediaDiscoverers)
            md.release();
        mMediaDiscoverers.clear();
        mDiscovererMediaArray.clear();
        if (mMedia != null) {
            mMedia.release();
            mMedia = null;
        }
        /* don't need to release the MediaList since it's either
         * associated with a Media or a MediaDiscoverer that will release it */
        mBrowserMediaList = null;
    }

    /**
     * Release the MediaBrowser.
     */
    public synchronized void release() {
        reset();
    }

    private void startMediaDiscoverer(String discovererName) {
        MediaDiscoverer md = new MediaDiscoverer(mLibVlc, discovererName);
        mMediaDiscoverers.add(md);
        final MediaList ml = md.getMediaList();
        ml.setEventListener(mDiscovererMediaListEventListener);
        md.start();
    }

    /**
     * Discover networks shares using available MediaDiscoverers
     */
    public synchronized void discoverNetworkShares() {
        reset();
        for (String discovererName : DISCOVERER_LIST)
            startMediaDiscoverer(discovererName);
    }

    /**
     * Discover networks shares using specified MediaDiscoverer
     * @param discovererName
     */
    public synchronized void discoverNetworkShares(String discovererName) {
        reset();
        startMediaDiscoverer(discovererName);
    }

    /**
     * Browse to the specified mrl.
     *
     * @param mrl
     */
    public synchronized void browse(String mrl) {
        final Media media = new Media(mLibVlc, mrl);
        browse(media);
        media.release();
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
        mBrowserMediaList = media.subItems();
        mBrowserMediaList.setEventListener(mBrowserMediaListEventListener);
        media.parseAsync(Media.Parse.ParseNetwork);
        mMedia = media;
    }

    /**
     * Get the number or media.
     */
    public synchronized int getMediaCount() {
        return mBrowserMediaList != null ? mBrowserMediaList.getCount() : mDiscovererMediaArray.size();
    }

    /**
     * Get a media at a specified index.
     */
    public synchronized Media getMediaAt(int index) {
        return index >= 0 && index < getMediaCount() ?
                mBrowserMediaList != null ? mBrowserMediaList.getMediaAt(index) :
                mDiscovererMediaArray.get(index) : null;
    }

    private MediaList.EventListener mBrowserMediaListEventListener = new MediaList.EventListener() {
        @Override
        public void onEvent(VLCObject.Event event) {
            if (mEventListener == null)
                return;
            final MediaList.Event mlEvent = (MediaList.Event) event;

            /*
             * We use an intermediate array here since more than one MediaDiscoverer can be used
             */
            switch (mlEvent.type) {
            case MediaList.Events.MediaListItemAdded:
                mEventListener.onMediaAdded(mlEvent.index, mlEvent.media);
                break;
            case MediaList.Events.MediaListItemDeleted:
                mEventListener.onMediaRemoved(mlEvent.index, mlEvent.media);
                break;
            case MediaList.Events.MediaListEndReached:
                mEventListener.onBrowseEnd();
            }
        }
    };

    private MediaList.EventListener mDiscovererMediaListEventListener = new MediaList.EventListener() {
        @Override
        public void onEvent(VLCObject.Event event) {
            if (mEventListener == null)
                return;
            final MediaList.Event mlEvent = (MediaList.Event) event;
            int index = -1;

            /*
             * We use an intermediate array here since more than one MediaDiscoverer can be used
             */
            switch (mlEvent.type) {
            case MediaList.Events.MediaListItemAdded:
                synchronized (MediaBrowser.this) {
                    /* one item can be found by severals discoverers */
                    boolean found = false;
                    for (Media media : mDiscovererMediaArray) {
                        if (media.getMrl().equals(mlEvent.media.getMrl())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mDiscovererMediaArray.add(mlEvent.media);
                        index = mDiscovererMediaArray.size() - 1;
                    }
                }
                if (index != -1)
                    mEventListener.onMediaAdded(index, mlEvent.media);
                break;
            case MediaList.Events.MediaListItemDeleted:
                synchronized (MediaBrowser.this) {
                    index = mDiscovererMediaArray.indexOf(mlEvent.media);
                    if (index != -1)
                        mDiscovererMediaArray.remove(index);
                }
                if (index != -1)
                    mEventListener.onMediaRemoved(index, mlEvent.media);
                break;
            case MediaList.Events.MediaListEndReached:
                mEventListener.onBrowseEnd();
            }
        }
    };
}
