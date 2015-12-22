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
import android.os.Message;
import android.os.Parcelable;
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

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.browser.BaseBrowserFragment;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.tv.CardPresenter;
import org.videolan.vlc.gui.tv.DetailsActivity;
import org.videolan.vlc.gui.tv.MediaItemDetails;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class NetworkBrowserFragment extends BrowseFragment implements BrowserFragmentInterface, MediaBrowser.EventListener, OnItemViewSelectedListener, OnItemViewClickedListener {

    public static final String TAG = "VLC/NetworkBrowserFragment";
    public static final String SELECTED_ITEM = "selected";
    public static int UPDATE_DISPLAY = 1;

    private static MediaBrowser.Discover DISCOVER_LIST[] = BuildConfig.DEBUG ? new MediaBrowser.Discover[] {
            MediaBrowser.Discover.UPNP,
            MediaBrowser.Discover.SMB,
    } : new MediaBrowser.Discover[] {
            MediaBrowser.Discover.UPNP,
    };

    ArrayObjectAdapter mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
    private MediaBrowser mMediaBrowser;
    private MediaWrapper mItemSelected;
    protected Map<String, ListItem> mMediaItemMap = new ArrayMap<String, ListItem>();
    private NetworkHandler mHandler = new NetworkHandler(this);

    private Uri mUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            String mrl = savedInstanceState.getString(BaseBrowserFragment.KEY_MRL);
            if (mrl != null)
                mUri = Uri.parse(mrl);
            mItemSelected = savedInstanceState.getParcelable(SELECTED_ITEM);
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null && intent.hasExtra(BaseBrowserFragment.KEY_MRL))
                mUri = Uri.parse(intent.getStringExtra(BaseBrowserFragment.KEY_MRL));
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
    }

    public void onResume() {
        super.onResume();
        if (mAdapter.size() == 0) {
            browse();
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
            outState.putString(BaseBrowserFragment.KEY_MRL, mUri.toString());
        if (mItemSelected != null) {
            outState.putParcelable(SELECTED_ITEM, mItemSelected);
        }
    }

    private void browse() {
        mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
        if (mMediaBrowser != null) {
            if (mUri != null)
                mMediaBrowser.browse(mUri);
            else
                mMediaBrowser.discoverNetworkShares(DISCOVER_LIST);
            ((BrowserActivityInterface)getActivity()).showProgress(true);
        }
    }

    @Override
    public void refresh() {
        mAdapter.clear();
        browse();
    }

    private void sort(){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                mMediaItemMap = new TreeMap<>(mMediaItemMap); //sort sections
                for (ListItem item : mMediaItemMap.values()) {
                    Collections.sort(item.mediaList, MediaComparators.byName);
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
        ((BrowserActivityInterface)getActivity()).updateEmptyView(mAdapter.size() == 0);
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

    public void showDetails() {
        if (mItemSelected == null)
            return;
        if (mItemSelected.getType() == MediaWrapper.TYPE_DIR) {
            Intent intent = new Intent(getActivity(),
                    DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", mItemSelected);
            intent.putExtra("item", (Parcelable) new MediaItemDetails(mItemSelected.getTitle(), mItemSelected.getArtist(), mItemSelected.getAlbum(), mItemSelected.getLocation()));
            startActivity(intent);
        }
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mItemSelected = (MediaWrapper)item;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder viewHolder, Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
        TvUtil.openMedia(getActivity(), item, null);
    }

    public static class ListItem {
        public String Letter;
        public ArrayList<MediaWrapper> mediaList;

        public ListItem(String letter, MediaWrapper MediaWrapper) {
            mediaList = new ArrayList<MediaWrapper>();
            if (MediaWrapper != null)
                mediaList.add(MediaWrapper);
            Letter = letter;
        }
    }

    private class NetworkHandler extends WeakHandler<NetworkBrowserFragment> {
        public NetworkHandler(NetworkBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            NetworkBrowserFragment owner = getOwner();
            if (owner != null && msg.what == UPDATE_DISPLAY)
                owner.updateList();
        }
    }
}
