/*
 * ************************************************************************
 *  MediaSortedFragment.java
 * *************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceManager;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.util.VLCInstance;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class MediaSortedFragment extends SortedBrowserFragment implements MediaBrowser.EventListener {
    protected Uri mUri;
    protected MediaBrowser mMediaBrowser;
    private boolean mShowHiddenFiles = false;
    private final Medialibrary mMedialibrary = VLCApplication.getMLInstance();

    private static Handler sBrowserHandler;

    protected void runOnBrowserThread(Runnable runnable) {
        sBrowserHandler.post(runnable);
    }

    abstract protected void browseRoot();

    @Override
    protected String getKey() {
        return mUri != null ? CURRENT_BROWSER_MAP+mUri.getPath() : CURRENT_BROWSER_MAP;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(KEY_URI);
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null)
                mUri = intent.getData();
        }
        if (sBrowserHandler == null) {
            HandlerThread handlerThread = new HandlerThread("vlc-browser", Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            handlerThread.start();
            sBrowserHandler = new Handler(handlerThread.getLooper());
        }
        mShowHiddenFiles = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("browser_show_hidden_files", false);
    }

    protected void browse() {
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                mMediaBrowser = new MediaBrowser(VLCInstance.get(), MediaSortedFragment.this, sBrowserHandler);
                if (mMediaBrowser != null) {
                    int flags = MediaBrowser.Flag.Interact;
                    if (mShowHiddenFiles)
                        flags |= MediaBrowser.Flag.ShowHiddenFiles;
                    if (mUri != null)
                        mMediaBrowser.browse(mUri, flags);
                    else
                        browseRoot();
                    ((BrowserActivityInterface)getActivity()).showProgress(true);
                }
            }
        });
    }

    public void onPause(){
        super.onPause();
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        runOnBrowserThread(releaseBrowser);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null)
            outState.putParcelable(KEY_URI, mUri);
    }

    @NonNull
    private MediaWrapper getMediaWrapper(MediaWrapper media) {
        MediaWrapper mw = null;
        Uri uri = media.getUri();
        if ((media.getType() == MediaWrapper.TYPE_AUDIO
                || media.getType() == MediaWrapper.TYPE_VIDEO)
                && "file".equals(uri.getScheme()))
            mw = mMedialibrary.getMedia(uri);
        if (mw == null)
            return media;
        return mw;
    }

    public void onMediaAdded(int index, Media media) {
        final MediaWrapper mw = getMediaWrapper(new MediaWrapper(media));
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                addMedia(mw);
                if (mUri == null) // we are at root level
                    sort();
                ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
                ((BrowserActivityInterface)getActivity()).showProgress(false);
            }
        });
    }

    public void onMediaRemoved(int index, Media media) {}

    public void onBrowseEnd() {
        releaseBrowser.run();
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isResumed()) {
                    sort();
                    mHandler.sendEmptyMessage(HIDE_LOADING);
                }
            }
        });
    }

    private Runnable releaseBrowser = new Runnable() {
        @Override
        public void run() {
            if (mMediaBrowser != null) {
                mMediaBrowser.release();
                mMediaBrowser = null;
            }
        }
    };
}
