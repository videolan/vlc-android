/*
 * *************************************************************************
 *  BrowserGridFragment.java
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

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.tv.DetailsActivity;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.MediaItemDetails;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class BrowserGridFragment extends GridFragment implements MediaBrowser.EventListener, OnItemViewSelectedListener, OnItemViewClickedListener, DetailsFragment {

    private MediaBrowser mMediaBrowser;
    private Uri mUri;
    List<MediaWrapper> mMediaList = null;
    private MediaWrapper mItemSelected;
    private boolean mShowHiddenFiles = false;

    private Handler mBrowserHandler;

    protected void runOnBrowserThread(Runnable runnable) {
        if (mBrowserHandler == null) {
            HandlerThread handlerThread = new HandlerThread("vlc-browser", Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            handlerThread.start();
            mBrowserHandler = new Handler(handlerThread.getLooper());
        }
        if (Looper.myLooper() == mBrowserHandler.getLooper())
            runnable.run();
        else
            mBrowserHandler.post(runnable);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setOnItemViewSelectedListener(this);
        setOnItemViewClickedListener(this);
        mShowHiddenFiles = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("browser_show_hidden_files", false);
    }

    public void onResume() {
        super.onResume();
        mMediaList = new ArrayList<>();
        if (mAdapter.size() == 0) runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                mMediaBrowser = new MediaBrowser(VLCInstance.get(), BrowserGridFragment.this);
                if (mUri != null) {
                    int flags = MediaBrowser.Flag.Interact;
                    if (mShowHiddenFiles) flags |= MediaBrowser.Flag.ShowHiddenFiles;
                    mMediaBrowser.browse(mUri, flags);
                } else
                    mMediaBrowser.discoverNetworkShares();
                ((BrowserActivityInterface)mContext).showProgress(true);
            }
        });
    }

    public void onPause(){
        super.onPause();
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                if (mMediaBrowser != null) mMediaBrowser.release();
                mMediaBrowser = null;
            }
        });
        ((BrowserActivityInterface)mContext).updateEmptyView(false);
    }
    @Override
    public void onMediaAdded(int index, Media media) {
        final MediaWrapper mw = new MediaWrapper(media);
        final int type = mw.getType();
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO || type == MediaWrapper.TYPE_DIR)
                    mMediaList.add(mw);
                if (mUri == null) { // we are at root level
                    mw.setDescription(mw.getUri().getScheme());
                    mAdapter.add(mw);
                }
                ((BrowserActivityInterface)getActivity()).showProgress(false);
            }
        });
    }

    @Override
    public void onMediaRemoved(int index, Media media) {
        final String uri = media.getUri().toString();
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                int position = -1;
                for (int i = 0; i < mMediaList.size(); ++i) {
                    if (TextUtils.equals(mMediaList.get(i).getUri().toString(), uri)) {
                        position = i;
                        break;
                    }
                }
                if (position == -1) return;
                mAdapter.removeItems(position, 1);
            }
        });
    }

    @Override
    public void onBrowseEnd() {
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                ((BrowserActivityInterface)getActivity()).showProgress(false);
                ((BrowserActivityInterface)getActivity()).updateEmptyView(mMediaList.isEmpty());
                sortList();
            }
        });
    }

    public void sortList(){
        List<MediaWrapper> files = new ArrayList<MediaWrapper>(), dirs = new ArrayList<MediaWrapper>();
        for (Object item : mMediaList){
            if (item instanceof MediaWrapper) {
                MediaWrapper media = (MediaWrapper) item;
                if (media.getType() == MediaWrapper.TYPE_DIR)
                    dirs.add(media);
                else
                    files.add(media);
            }
        }
        Collections.sort(dirs, MediaComparators.byName);
        Collections.sort(files, MediaComparators.byName);
        mMediaList.clear();
        mMediaList.addAll(dirs);
        mMediaList.addAll(files);
        mAdapter.clear();
        mAdapter.addAll(0, mMediaList);
        mAdapter.notifyArrayItemRangeChanged(0, mMediaList.size());
    }

    public void showDetails() {
        if (mItemSelected.getType() == MediaWrapper.TYPE_DIR) {
            Intent intent = new Intent(getActivity(),
                    DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", mItemSelected);
            intent.putExtra("item", new MediaItemDetails(mItemSelected.getTitle(),
                    mItemSelected.getArtist(), mItemSelected.getAlbum(),
                    mItemSelected.getLocation(), mItemSelected.getArtworkURL()));
            startActivity(intent);
        }
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mItemSelected = (MediaWrapper)item;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        MediaWrapper media = (MediaWrapper) item;
        if (media.getType() == MediaWrapper.TYPE_DIR)
            TvUtil.browseFolder(getActivity(), MainTvActivity.HEADER_NETWORK, ((MediaWrapper) item).getUri());
        else
            TvUtil.openMedia(getActivity(), item, null);
    }
}
