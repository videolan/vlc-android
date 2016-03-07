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
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import org.videolan.libvlc.Media;
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
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class SortedBrowserFragment extends BrowseFragment implements BrowserFragmentInterface, OnItemViewSelectedListener, OnItemViewClickedListener, IVideoBrowser, DetailsFragment {

    public static final String TAG = "VLC/SortedBrowserFragment";

    public static final String KEY_URI = "uri";
    public static final String SELECTED_ITEM = "selected";
    public static final int UPDATE_DISPLAY = 1;
    public static final int UPDATE_ITEM = 2;
    public static final int HIDE_LOADING = 3;


    protected ArrayObjectAdapter mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
    protected MediaWrapper mItemSelected;
    protected Map<String, ListItem> mMediaItemMap = new ArrayMap<>();
    SimpleArrayMap<String, Integer> mMediaIndex = new SimpleArrayMap<>();
    protected BrowserHandler mHandler = new BrowserHandler(this);

    abstract protected void browse();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mItemSelected = savedInstanceState.getParcelable(SELECTED_ITEM);
        }
        setOnItemViewClickedListener(this);
        setOnItemViewSelectedListener(this);
        setAdapter(mAdapter);

        // UI setting
        setHeadersState(HEADERS_ENABLED);
        setBrandColor(getResources().getColor(R.color.orange800));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHeadersState(HEADERS_HIDDEN);
        if (mAdapter.size() == 0)
            browse();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mItemSelected != null)
            outState.putParcelable(SELECTED_ITEM, mItemSelected);
    }

    public void showDetails() {
        if (mItemSelected == null)
            return;
        Intent intent = new Intent(getActivity(),
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
                mMediaItemMap = new TreeMap<>(mMediaItemMap); //sort sections
                for (ListItem item : mMediaItemMap.values()) {
                    Collections.sort(item.mediaList, MediaComparators.byFileType);
                }
                mHandler.sendEmptyMessage(UPDATE_DISPLAY);
            }
        });
    }

    @Override
    public void updateList() {
        mAdapter.clear();
        ArrayObjectAdapter adapter;
        HeaderItem header;
        for (ListItem item : mMediaItemMap.values()){
            adapter = new ArrayObjectAdapter(new CardPresenter(getActivity()));
            header = new HeaderItem(0, item.Letter);
            adapter.addAll(0, item.mediaList);
            mAdapter.add(new ListRow(header, adapter));
        }
        mHandler.sendEmptyMessageDelayed(HIDE_LOADING, 3000);
    }

    protected void addMedia(Media media){
        addMedia(new MediaWrapper(media));
    }

    protected void addMedia(MediaWrapper mw){
        int type = mw.getType();
        if (type != MediaWrapper.TYPE_AUDIO && type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_DIR)
            return;
        String letter = mw.getTitle().substring(0, 1).toUpperCase();
        if (mMediaItemMap.containsKey(letter)){
            mMediaItemMap.get(letter).mediaList.add(mw);
        } else {
            ListItem item = new ListItem(letter, mw);
            mMediaItemMap.put(letter, item);
        }
        ((BrowserActivityInterface)getActivity()).showProgress(false);
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
        mHandler.removeMessages(HIDE_LOADING);
    }

    public boolean isEmpty() {
        return mMediaItemMap.isEmpty();
    }

    @Override
    public void setItemToUpdate(MediaWrapper item) {
        mHandler.obtainMessage(UPDATE_ITEM, item).sendToTarget();
    }

    public void updateItem(MediaWrapper item) {
        if (mAdapter != null && mMediaIndex != null && item != null
                && mMediaIndex.containsKey(item.getLocation()))
            mAdapter.notifyArrayItemRangeChanged(mMediaIndex.get(item.getLocation()).intValue(), 1);
    }

    @Override
    public void showProgressBar() {}

    @Override
    public void hideProgressBar() {}

    @Override
    public void clearTextInfo() {}

    @Override
    public void sendTextInfo(String info, int progress, int max) {}

    public static class ListItem {
        public String Letter;
        public ArrayList<MediaWrapper> mediaList;

        public ListItem(String letter, MediaWrapper mediaWrapper) {
            mediaList = new ArrayList<>();
            if (mediaWrapper != null)
                mediaList.add(mediaWrapper);
            Letter = letter;
        }
    }

    protected static class BrowserHandler extends WeakHandler<SortedBrowserFragment> {
        public BrowserHandler(SortedBrowserFragment owner) {
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
