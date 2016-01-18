/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.VLCInstance;

import java.io.File;

public class DirectoryBrowserFragment extends SortedBrowserFragment implements MediaBrowser.EventListener {

    public static final String TAG = "VLC/NetworkBrowserFragment";

    private MediaBrowser mMediaBrowser;

    private Uri mUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mUri = savedInstanceState.getParcelable(KEY_URI);
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null && intent.hasExtra(KEY_URI))
                mUri = intent.getParcelableExtra(KEY_URI);
        }
    }

    public void onPause(){
        super.onPause();
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null)
            outState.putParcelable(KEY_URI, mUri);
    }

    protected void browse() {
        ((BrowserActivityInterface)getActivity()).showProgress(true);
        mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
        if (mMediaBrowser != null) {
            if (mUri != null)
                mMediaBrowser.browse(mUri);
            else
                browseRoot();
        }
    }

    protected void browseRoot() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                String storages[] = AndroidDevices.getMediaDirectories();
                MediaWrapper directory;
                for (String mediaDirLocation : storages) {
                    if (!(new File(mediaDirLocation).exists()))
                        continue;
                    directory = new MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation));
                    directory.setType(MediaWrapper.TYPE_DIR);
                    if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                        directory.setTitle(getString(R.string.internal_memory));
                    addMedia(directory);
                }
                sort();
            }
        });
    }

    private void addMedia(Media media){
        addMedia(new MediaWrapper(media));
    }

    private void addMedia(MediaWrapper media){
        int type = media.getType();
        if (type != MediaWrapper.TYPE_AUDIO && type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_DIR)
            return;
        String letter = media.getTitle().substring(0, 1).toUpperCase();
        if (mMediaItemMap.containsKey(letter)){
            mMediaItemMap.get(letter).mediaList.add(media);
        } else {
            ListItem item = new ListItem(letter, media);
            mMediaItemMap.put(letter, item);
        }
        ((BrowserActivityInterface)getActivity()).showProgress(false);
    }

    public void onMediaAdded(int index, Media media) {
        addMedia(media);

        if (mUri == null) { // we are at root level
            sort();
        }
        ((BrowserActivityInterface)getActivity()).showProgress(false);
    }

    public void onMediaRemoved(int index, Media media) {}

    public void onBrowseEnd() {
        ((BrowserActivityInterface)getActivity()).showProgress(false);
        ((BrowserActivityInterface)getActivity()).updateEmptyView(mAdapter.size() == 0);
        sort();
    }
}
