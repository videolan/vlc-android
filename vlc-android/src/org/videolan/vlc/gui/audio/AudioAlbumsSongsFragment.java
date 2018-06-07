/*****************************************************************************
 * AudioAlbumsSongsFragment.java
 *****************************************************************************
 * Copyright © 2011-2016 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaylistActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.viewmodels.audio.AlbumModel;
import org.videolan.vlc.viewmodels.audio.AudioModel;
import org.videolan.vlc.viewmodels.audio.TracksModel;

import java.util.ArrayList;
import java.util.List;

public class AudioAlbumsSongsFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, TabLayout.OnTabSelectedListener {

    private final static String TAG = "VLC/AudioAlbumsSongsFragment";

    protected Handler mHandler = new Handler(Looper.getMainLooper());
    private AlbumModel albumModel;
    private TracksModel tracksModel;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ViewPager mViewPager;
    TabLayout mTabLayout;
    private RecyclerView[] mLists;
    private AudioModel[] audioModels;
    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private FastScroller mFastScroller;
    //private View mSearchButtonView;

    private final static int MODE_ALBUM = 0;
    private final static int MODE_SONG = 1;
    private final static int MODE_TOTAL = 2; // Number of audio mProvider modes

    private MediaLibraryItem mItem;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioAlbumsSongsFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mItem = (MediaLibraryItem) (savedInstanceState != null ?
                            savedInstanceState.getParcelable(AudioBrowserFragment.TAG_ITEM) :
                            getArguments().getParcelable(AudioBrowserFragment.TAG_ITEM));
        albumModel = ViewModelProviders.of(this, new AlbumModel.Factory(mItem)).get(AlbumModel.class);
        tracksModel = ViewModelProviders.of(this, new TracksModel.Factory(mItem)).get(TracksModel.class);
        audioModels = new AudioModel[] {albumModel, tracksModel};
    }

    @Override
    public String getTitle() {
        return mItem.getTitle();
    }

    @Override
    public AudioModel getViewModel() {
        return audioModels[mViewPager.getCurrentItem()];
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final View v = inflater.inflate(R.layout.audio_albums_songs, container, false);

        mViewPager = v.findViewById(R.id.pager);
        final RecyclerView albumsList = (RecyclerView) mViewPager.getChildAt(MODE_ALBUM);
        final RecyclerView songsList = (RecyclerView) mViewPager.getChildAt(MODE_SONG);

        mLists = new RecyclerView[]{albumsList, songsList};
        final String[] titles = new String[] {getString(R.string.albums), getString(R.string.songs)};
        mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this);
        mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this);
        mAdapters = new AudioBrowserAdapter[]{mAlbumsAdapter, mSongsAdapter};

        songsList.setAdapter(mSongsAdapter);
        albumsList.setAdapter(mAlbumsAdapter);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mFastScroller = v.findViewById(R.id.songs_fast_scroller);

        mViewPager.setOnTouchListener(mSwipeFilter);
        mTabLayout = v.findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        mSwipeRefreshLayout = v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final RecyclerView.RecycledViewPool rvp = new RecyclerView.RecycledViewPool();
        for (RecyclerView rv : mLists) {
            rv.setLayoutManager(new LinearLayoutManager(view.getContext()));
            final LinearLayoutManager llm = new LinearLayoutManager(getActivity());
            llm.setRecycleChildrenOnDetach(true);
            rv.setLayoutManager(llm);
            rv.setRecycledViewPool(rvp);
        }
        mFabPlay.setImageResource(R.drawable.ic_fab_play);
        mTabLayout.addOnTabSelectedListener(this);
        albumModel.getSections().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> albums) {
                if (albums != null) mAlbumsAdapter.update(albums);
            }
        });
        tracksModel.getSections().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> tracks) {
                if (tracks != null) mSongsAdapter.update(tracks);
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (SecondaryActivity) context;
    }

    @Override
    public void onRefresh() {
        mActivity.closeSearchView();
        albumModel.refresh();
        tracksModel.refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, mItem);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        super.onUpdateFinished(adapter);
        mFastScroller.setRecyclerView(getCurrentRV());
        mSwipeRefreshLayout.setRefreshing(false);
        if (mAlbumsAdapter.isEmpty()) mViewPager.setCurrentItem(1);
    }

    /*
         * Disable Swipe Refresh while scrolling horizontally
         */
    private View.OnTouchListener mSwipeFilter = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mSwipeRefreshLayout.setEnabled(event.getAction() == MotionEvent.ACTION_UP);
            return false;
        }
    };

    public void clear() {
        mAlbumsAdapter.clear();
        mSongsAdapter.clear();
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null) {
            super.onClick(v, position, item);
            return;
        }
        if (item instanceof Album) {
            final Intent i = new Intent(getActivity(), PlaylistActivity.class);
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item);
            startActivity(i);
        } else
            MediaUtils.openMedia(v.getContext(), (MediaWrapper) item);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.invalidateOptionsMenu();
        mFastScroller.setRecyclerView(mLists[tab.getPosition()]);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        stopActionMode();
        onDestroyActionMode((AudioBrowserAdapter) mLists[tab.getPosition()].getAdapter());
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        mLists[tab.getPosition()].smoothScrollToPosition(0);
    }

    public AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) getCurrentRV().getAdapter();
    }

    private RecyclerView getCurrentRV() {
        return mLists[mViewPager.getCurrentItem()];
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(enable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onFabPlayClick(View view) {
        final List<MediaWrapper> list ;
        if (mViewPager.getCurrentItem() == 0) {
            list = new ArrayList<>();
            for (MediaLibraryItem item : mAlbumsAdapter.getMediaItems())
                list.addAll(Util.arrayToArrayList(item.getTracks()));
        } else {
            list = (List<MediaWrapper>) (List<?>) mSongsAdapter.getMediaItems();
        }
        MediaUtils.openList(getActivity(), list, 0);
    }
}
