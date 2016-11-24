/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.SearchAggregate;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import java.util.Arrays;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SearchFragment extends android.support.v17.leanback.app.SearchFragment
        implements android.support.v17.leanback.app.SearchFragment.SearchResultProvider {

    private static final String TAG = "SearchFragment";

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler = new Handler();
    private SearchRunnable mDelayedLoad;
    protected Activity mActivity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setSearchResultProvider(this);
        setOnItemViewClickedListener(getDefaultItemClickedListener());
        mDelayedLoad = new SearchRunnable();
        mActivity = getActivity();
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    private void queryByWords(String words) {
        if (words == null || words.length() < 3)
            return;
        mRowsAdapter.clear();
        if (!TextUtils.isEmpty(words) && words.length() > 2) {
            mDelayedLoad.setSearchQuery(words);
            VLCApplication.runBackground(mDelayedLoad);
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
        SearchAggregate searchAggregate = VLCApplication.getMLInstance().search(query);
        CardPresenter cp = new CardPresenter(mActivity);
        final ArrayObjectAdapter videoAdapter = new ArrayObjectAdapter(cp);
        videoAdapter.addAll(0, Arrays.asList(searchAggregate.getMediaSearchAggregate().getOthers()));
        final ArrayObjectAdapter episodesAdapter = new ArrayObjectAdapter(cp);
        episodesAdapter.addAll(0, Arrays.asList(searchAggregate.getMediaSearchAggregate().getEpisodes()));
        final ArrayObjectAdapter moviesAdapter = new ArrayObjectAdapter(cp);
        moviesAdapter.addAll(0, Arrays.asList(searchAggregate.getMediaSearchAggregate().getMovies()));
        final ArrayObjectAdapter songsAdapter = new ArrayObjectAdapter(cp);
        songsAdapter.addAll(0, Arrays.asList(searchAggregate.getMediaSearchAggregate().getTracks()));
        final ArrayObjectAdapter artistsAdapter = new ArrayObjectAdapter(cp);
        artistsAdapter.addAll(0, Arrays.asList(searchAggregate.getArtists()));
        final ArrayObjectAdapter albumsAdapter = new ArrayObjectAdapter(cp);
        albumsAdapter.addAll(0, Arrays.asList(searchAggregate.getAlbums()));
        final ArrayObjectAdapter genresAdapter = new ArrayObjectAdapter(cp);
        genresAdapter.addAll(0, Arrays.asList(searchAggregate.getGenres()));
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (videoAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.videos)), videoAdapter));
                if (episodesAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.episodes)), episodesAdapter));
                if (moviesAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.movies)), moviesAdapter));
                if (songsAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.songs)), songsAdapter));
                if (artistsAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.artists)), artistsAdapter));
                if (albumsAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.albums)), albumsAdapter));
                if (genresAdapter.size() > 0)
                    mRowsAdapter.add(new ListRow(new HeaderItem(0, getResources().getString(R.string.genres)), genresAdapter));
            }
        });
    }

    protected OnItemViewClickedListener getDefaultItemClickedListener() {
        return new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof MediaWrapper)
                    TvUtil.openMedia(mActivity, item, row);
                else
                    TvUtil.openAudioCategory(mActivity, (MediaLibraryItem) item);
                getActivity().finish();
            }
        };
    }

    private class SearchRunnable implements Runnable {

        private volatile String searchQuery;

        SearchRunnable() {}

        public void run() {
            loadRows(searchQuery);
        }

        void setSearchQuery(String value) {
            this.searchQuery = value;
        }
    }
}
