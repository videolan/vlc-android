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

import android.net.Uri;
import android.support.annotation.MainThread;
import android.util.Log;

import java.util.ArrayList;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaDiscoverer;
import org.videolan.libvlc.MediaList;

public class MediaBrowser {
    private static final String TAG = "MediaBrowser";

    private final LibVLC mLibVlc;
    private final ArrayList<MediaDiscoverer> mMediaDiscoverers = new ArrayList<MediaDiscoverer>();
    private final ArrayList<Media> mDiscovererMediaArray = new ArrayList<Media>();
    private MediaList mBrowserMediaList;
    private Media mMedia;
    private EventListener mEventListener;
    private boolean mAlive;

    private static final String IGNORE_LIST_OPTION =  ":ignore-filetypes=";
    private String mIgnoreList = "db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,cue,ssa";

    /**
     * Listener called when medias are added or removed.
     */
    public interface EventListener {
        /**
         * Received when a new media is added.
         * @param index
         * @param media
         */
        void onMediaAdded(int index, Media media);
        /**
         * Received when a media is removed (Happens only when you discover networks)
         * @param index
         * @param media Released media, but cached attributes are still
         * available (like media.getMrl())
         */
        void onMediaRemoved(int index, Media media);
        /**
         * Called when browse ended.
         * It won't be called when you discover networks
         */
        void onBrowseEnd();
    }

    public MediaBrowser(LibVLC libvlc, EventListener listener) {
        mLibVlc = libvlc;
        mLibVlc.retain();
        mEventListener = listener;
        mAlive = true;
    }

    private void reset() {
        for (MediaDiscoverer md : mMediaDiscoverers)
            md.release();
        mMediaDiscoverers.clear();
        mDiscovererMediaArray.clear();
        if (mMedia != null) {
            mMedia.release();
            mMedia = null;
        }

        if (mBrowserMediaList != null) {
            mBrowserMediaList.release();
            mBrowserMediaList = null;
        }
    }

    /**
     * Release the MediaBrowser.
     */
    @MainThread
    public void release() {
        reset();
        if (!mAlive)
            throw new IllegalStateException("MediaBrowser released more than one time");
        mLibVlc.release();
        mAlive = false;
    }

    /**
     * Reset this media browser and register a new EventListener
     * @param eventListener new EventListener for this browser
     */
    @MainThread
    public void changeEventListener(EventListener eventListener){
        reset();
        mEventListener = eventListener;
    }

    private void startMediaDiscoverer(String discovererName) {
        MediaDiscoverer md = new MediaDiscoverer(mLibVlc, discovererName);
        mMediaDiscoverers.add(md);
        final MediaList ml = md.getMediaList();
        ml.setEventListener(mDiscovererMediaListEventListener);
        ml.release();
        md.start();
    }

    /**
     * Discover all networks shares
     */
    @MainThread
    public void discoverNetworkShares() {
        reset();

        final MediaDiscoverer.Description descriptions[] =
                MediaDiscoverer.list(mLibVlc, MediaDiscoverer.Description.Category.Lan);
        if (descriptions == null)
            return;
        for (MediaDiscoverer.Description description : descriptions) {
            Log.i(TAG, "starting " + description.name + " discover (" + description.longName + ")");
            startMediaDiscoverer(description.name);
        }
    }

    /**
     * Discover networks shares using a specified Discoverer
     * @param serviceName see {@link MediaDiscoverer.Description.Category#name}
     */
    @MainThread
    public void discoverNetworkShares(String serviceName) {
        reset();
        startMediaDiscoverer(serviceName);
    }

    /**
     * Browse to the specified local path starting with '/'.
     *
     * @param path
     * @param interact true if browsing could fire up dialogs
     */
    @MainThread
    public void browse(String path, boolean interact) {
        final Media media = new Media(mLibVlc, path);
        browse(media, interact);
        media.release();
    }

    /**
     * Browse to the specified uri.
     *
     * @param uri
     * @param interact true if browsing could fire up dialogs
     */
    @MainThread
    public void browse(Uri uri, boolean interact) {
        final Media media = new Media(mLibVlc, uri);
        browse(media, interact);
        media.release();
    }

    /**
     * Browse to the specified media.
     *
     * @param media Can be a media returned by MediaBrowser.
     * @param interact true if browsing could fire up dialogs
     */
    @MainThread
    public void browse(Media media, boolean interact) {
        /* media can be associated with a medialist,
         * so increment ref count in order to don't clean it with the medialist
         */
        media.retain();
        media.addOption(IGNORE_LIST_OPTION + mIgnoreList);
        int flags = Media.Parse.ParseNetwork;
        if (interact)
            flags |= Media.Parse.DoInteract;
        reset();
        mBrowserMediaList = media.subItems();
        mBrowserMediaList.setEventListener(mBrowserMediaListEventListener);
        media.parseAsync(flags);
        mMedia = media;
    }

    /**
     * Get the number or media.
     */
    @MainThread
    public int getMediaCount() {
        return mBrowserMediaList != null ? mBrowserMediaList.getCount() : mDiscovererMediaArray.size();
    }

    /**
     * Get a media at a specified index. Should be released with {@link #release()}.
     */
    @MainThread
    public Media getMediaAt(int index) {
        if (index < 0 || index >= getMediaCount())
            throw new IndexOutOfBoundsException();
        final Media media = mBrowserMediaList != null ? mBrowserMediaList.getMediaAt(index) :
                mDiscovererMediaArray.get(index);
        media.retain();
        return media;
    }

    /**
     * Override the extensions list to be ignored in browsing
     * default is "db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,cue,ssa"
     *
     * @param list files extensions to be ignored by browser
     */
    @MainThread
    public void setIgnoreFileTypes(String list) {
        mIgnoreList = list;
    }

    private final MediaList.EventListener mBrowserMediaListEventListener = new MediaList.EventListener() {
        @Override
        public void onEvent(MediaList.Event event) {
            if (mEventListener == null)
                return;
            final MediaList.Event mlEvent = event;

            switch (mlEvent.type) {
            case MediaList.Event.ItemAdded:
                mEventListener.onMediaAdded(mlEvent.index, mlEvent.media);
                break;
            case MediaList.Event.ItemDeleted:
                mEventListener.onMediaRemoved(mlEvent.index, mlEvent.media);
                break;
            case MediaList.Event.EndReached:
                mEventListener.onBrowseEnd();
            }
        }
    };

    private final MediaList.EventListener mDiscovererMediaListEventListener = new MediaList.EventListener() {
        @Override
        public void onEvent(MediaList.Event event) {
            if (mEventListener == null)
                return;
            final MediaList.Event mlEvent = event;
            int index = -1;

            /*
             * We use an intermediate array here since more than one MediaDiscoverer can be used
             */
            switch (mlEvent.type) {
            case MediaList.Event.ItemAdded:
                /* one item can be found by severals discoverers */
                boolean found = false;
                for (Media media : mDiscovererMediaArray) {
                    if (media.getUri().toString().equalsIgnoreCase(mlEvent.media.getUri().toString())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mDiscovererMediaArray.add(mlEvent.media);
                    index = mDiscovererMediaArray.size() - 1;
                }if (index != -1)
                    mEventListener.onMediaAdded(index, mlEvent.media);
                break;
            case MediaList.Event.ItemDeleted:
                index = mDiscovererMediaArray.indexOf(mlEvent.media);
                if (index != -1)
                    mDiscovererMediaArray.remove(index);
                if (index != -1)
                    mEventListener.onMediaRemoved(index, mlEvent.media);
                break;
            case MediaList.Event.EndReached:
                mEventListener.onBrowseEnd();
            }
        }
    };
}