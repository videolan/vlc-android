/*
 * ************************************************************************
 *  MediaSortedFragment.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.util.VLCInstance;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class MediaSortedFragment extends SortedBrowserFragment implements MediaBrowser.EventListener {
    protected Uri mUri;
    protected MediaBrowser mMediaBrowser;
    boolean goBack = false;

    abstract protected void browseRoot();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mUri = savedInstanceState.getParcelable(KEY_URI);
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null)
                mUri = intent.getData();
        }
    }

    protected void browse() {
        mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
        if (mMediaBrowser != null) {
            if (mUri != null)
                mMediaBrowser.browse(mUri, true);
            else
                browseRoot();
            ((BrowserActivityInterface)getActivity()).showProgress(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (goBack)
            getActivity().finish();
    }

    public void onPause(){
        super.onPause();
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
    }

    private void releaseBrowser() {
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        releaseBrowser();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null)
            outState.putParcelable(KEY_URI, mUri);
    }

    public void onMediaAdded(int index, Media media) {
        addMedia(media);

        if (mUri == null) { // we are at root level
            sort();
        }
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
        ((BrowserActivityInterface)getActivity()).showProgress(false);
    }

    public void onMediaRemoved(int index, Media media) {}

    public void onBrowseEnd() {
        releaseBrowser();
        if (isResumed()) {
            sort();
            mHandler.sendEmptyMessage(HIDE_LOADING);
        } else
            goBack = true;
    }
}
