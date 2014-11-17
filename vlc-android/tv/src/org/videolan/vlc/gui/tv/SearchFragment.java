/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.Row;
import android.text.TextUtils;

public class SearchFragment extends android.support.v17.leanback.app.SearchFragment
implements android.support.v17.leanback.app.SearchFragment.SearchResultProvider {

	private static final String TAG = "SearchFragment";
    private static final int SEARCH_DELAY_MS = 300;

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler = new Handler();
    private SearchRunnable mDelayedLoad;
    protected Activity mActivity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setSearchResultProvider(this);
        setOnItemClickedListener(getDefaultItemClickedListener());
        mDelayedLoad = new SearchRunnable();
        mActivity = getActivity();
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    private void queryByWords(String words) {
        mRowsAdapter.clear();
        if (!TextUtils.isEmpty(words)) {
            mDelayedLoad.setSearchQuery(words);
            mHandler.removeCallbacks(mDelayedLoad);
            mHandler.postDelayed(mDelayedLoad, SEARCH_DELAY_MS);
        }
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        queryByWords(newQuery);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        queryByWords(query);
        return true;
    }

    private void loadRows(String query) {
        ArrayList<Media> mediaList = MediaLibrary.getInstance().getMediaItems();
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        for (Media media : mediaList) {
            if (media.getTitle().toLowerCase().indexOf(query.toLowerCase()) >= 0
                    || media.getLocation().toLowerCase().indexOf(query.toLowerCase()) >= 0) {
                listRowAdapter.add(media);
            }
        }
        HeaderItem header = new HeaderItem(0, getResources().getString(R.string.search_results),
                null);
        mRowsAdapter.add(new ListRow(header, listRowAdapter));
    }

    protected OnItemClickedListener getDefaultItemClickedListener() {
        return new OnItemClickedListener() {
            @Override
            public void onItemClicked(Object item, Row row) {
                if (item instanceof Media) {
                    TvUtil.openMedia(mActivity, (Media) item, row);
                }
            }
        };
    }

    private class SearchRunnable implements Runnable {

        private volatile String searchQuery;

        public SearchRunnable() {}

        public void run() {
            loadRows(searchQuery);
        }

        public void setSearchQuery(String value) {
            this.searchQuery = value;
        }
    }
}
