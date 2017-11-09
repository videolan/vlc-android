/*
 * *************************************************************************
 *  SortedBrowserFragment.java
 * **************************************************************************
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseSupportFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import org.videolan.libvlc.Media;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.tv.CardPresenter;
import org.videolan.vlc.gui.tv.DetailsActivity;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.MediaItemDetails;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class SortedBrowserFragment extends BrowseSupportFragment implements BrowserFragmentInterface, OnItemViewSelectedListener, OnItemViewClickedListener, DetailsFragment {

    public static final String TAG = "VLC/SortedBrowserFragment";

    public static final String KEY_URI = "uri";
    public static final String SELECTED_ITEM = "selected";
    public static final String CURRENT_BROWSER_LIST = "CURRENT_BROWSER_LIST";
    public static final String CURRENT_BROWSER_MAP = "CURRENT_BROWSER_MAP";

    public static final int UPDATE_DISPLAY = 1;
    public static final int UPDATE_ITEM = 2;
    public static final int HIDE_LOADING = 3;


    protected ArrayObjectAdapter mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
    protected MediaWrapper mItemSelected;
    protected Map<String, ListItem> mMediaItemMap = new ArrayMap<>(), mTempMap;
    protected final SimpleArrayMap<String, Integer> mMediaIndex = new SimpleArrayMap<>();
    List<MediaWrapper> mVideosList = new ArrayList<>();
    protected final BrowserHandler mHandler = new BrowserHandler(this);
    private BackgroundManager mBackgroundManager;

    abstract protected void browse();
    abstract protected String getKey();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            mItemSelected = savedInstanceState.getParcelable(SELECTED_ITEM);
        else {
            setOnItemViewClickedListener(this);
            setAdapter(mAdapter);
        }

        // UI setting
        setHeadersState(HEADERS_ENABLED);
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.orange800));
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.setAutoReleaseOnStop(false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHeadersState(HEADERS_HIDDEN);
        setOnItemViewSelectedListener(this);
        if (savedInstanceState == null)
            browse();
        else {
            synchronized (mMediaItemMap) {
                mMediaItemMap = (Map<String, ListItem>) VLCApplication.getData(getKey());
            }
            if (mMediaItemMap != null)
                sort();
            else
                getActivity().finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mItemSelected != null)
            TvUtil.updateBackground(mBackgroundManager, mItemSelected);
    }

    @Override
    public void onResume() {
        super.onResume();
        VLCApplication.storeData(CURRENT_BROWSER_LIST, mVideosList);
        if (!mBackgroundManager.isAttached())
            mBackgroundManager.attachToView(getView());
    }

    @Override
    public void onPause() {
        super.onPause();
        TvUtil.releaseBackgroundManager(mBackgroundManager);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mItemSelected != null)
            outState.putParcelable(SELECTED_ITEM, mItemSelected);
        VLCApplication.storeData(getKey(), mMediaItemMap);
    }

    public void showDetails() {
        if (mItemSelected == null)
            return;
        final Intent intent = new Intent(getActivity(),
                DetailsActivity.class);
        // pass the item information
        intent.putExtra("media", mItemSelected);
        intent.putExtra("item", new MediaItemDetails(mItemSelected.getTitle(),
                mItemSelected.getArtist(), mItemSelected.getAlbum(),
                mItemSelected.getLocation(), mItemSelected.getArtworkURL()));
        startActivity(intent);
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mItemSelected = (MediaWrapper)item;
        TvUtil.updateBackground(mBackgroundManager, item);
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder viewHolder, Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
        MediaWrapper media = (MediaWrapper) item;
        if (media.getType() == MediaWrapper.TYPE_DIR)
            TvUtil.browseFolder(getActivity(), getCategoryId(), ((MediaWrapper) item).getUri());
        else
            TvUtil.openMedia(getActivity(), item, null);
    }

    private long getCategoryId() {
        if (this instanceof NetworkBrowserFragment)
            return MainTvActivity.HEADER_NETWORK;
        else if (this instanceof DirectoryBrowserFragment)
            return MainTvActivity.HEADER_DIRECTORIES;
        return -1;
    }

    @Override
    public void refresh() {
        mMediaItemMap.clear();
        mMediaIndex.clear();
        mAdapter.clear();
        browse();
    }

    protected void sort(){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                synchronized (mMediaItemMap) {
                    mTempMap = new TreeMap<>(mMediaItemMap); //sort sections
                }
                for (ListItem item : mMediaItemMap.values())
                    Collections.sort(item.mediaList, MediaComparators.byFileType);
                mHandler.sendEmptyMessage(UPDATE_DISPLAY);
                VLCApplication.storeData(CURRENT_BROWSER_LIST, mVideosList);
            }
        });
    }

    @Override
    public void updateList() {
        Activity activity = getActivity();
        if (activity == null)
            return;
        mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mAdapter);
        ArrayObjectAdapter adapter;
        HeaderItem header;
        synchronized (mMediaItemMap) {
            if (mTempMap != null)
                mMediaItemMap = mTempMap;
        }
        for (ListItem item : mMediaItemMap.values()) {
            adapter = new ArrayObjectAdapter(new CardPresenter(activity));
            header = new HeaderItem(0, item.Letter);
            adapter.addAll(0, item.mediaList);
            mAdapter.add(new ListRow(header, adapter));
        }
        mHandler.sendEmptyMessageDelayed(HIDE_LOADING, 3000);
    }

    protected void addMedia(Media media){
        addMedia(new MediaWrapper(media));
    }

    protected void addMedia(MediaWrapper mw) {
        int type = mw.getType();
        if (type != MediaWrapper.TYPE_AUDIO && type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_DIR)
            return;
        String letter = mw.getTitle().substring(0, 1).toUpperCase();
        if (mMediaItemMap.containsKey(letter)) {
            int position = mMediaItemMap.get(letter).mediaList.indexOf(mw);
            if (position != -1) {
                mMediaItemMap.get(letter).mediaList.set(position, mw);
            } else
                mMediaItemMap.get(letter).mediaList.add(mw);
        } else {
            ListItem item = new ListItem(letter, mw);
            mMediaItemMap.put(letter, item);
        }
        final Activity activity = getActivity();
        if (activity != null) {
            ((BrowserActivityInterface)activity).showProgress(false);
            ((BrowserActivityInterface)activity).updateEmptyView(false);
        }
        mHandler.removeMessages(HIDE_LOADING);
    }

    public boolean isEmpty() {
        return mMediaItemMap.isEmpty();
    }

    public void updateItem(MediaWrapper item) {
        if (mAdapter != null && mMediaIndex != null && item != null
                && mMediaIndex.containsKey(item.getLocation()))
            mAdapter.notifyArrayItemRangeChanged(mMediaIndex.get(item.getLocation()), 1);
    }

    public static class ListItem {
        String Letter;
        public List<MediaWrapper> mediaList;

        ListItem(String letter, MediaWrapper mediaWrapper) {
            mediaList = new ArrayList<>();
            if (mediaWrapper != null)
                mediaList.add(mediaWrapper);
            Letter = letter;
        }
    }

    protected static class BrowserHandler extends WeakHandler<SortedBrowserFragment> {
        BrowserHandler(SortedBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            SortedBrowserFragment owner = getOwner();
            if (owner == null)
                return;
            switch (msg.what) {
                case UPDATE_ITEM:
                    owner.updateItem((MediaWrapper)msg.obj);
                    break;
                case UPDATE_DISPLAY:
                    owner.updateList();
                    break;
                case HIDE_LOADING:
                    if (owner.getActivity() != null) {
                        ((VerticalGridActivity)owner.getActivity()).showProgress(false);
                        ((VerticalGridActivity)owner.getActivity()).updateEmptyView(owner.isEmpty());
                    }
                    break;
            }
        }
    }
}
