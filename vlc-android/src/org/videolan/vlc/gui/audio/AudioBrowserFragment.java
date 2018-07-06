/*****************************************************************************
 * AudioBrowserFragment.java
 *****************************************************************************
 * Copyright © 2011-2017 VLC authors and VideoLAN
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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaylistActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.viewmodels.audio.AlbumModel;
import org.videolan.vlc.viewmodels.audio.ArtistModel;
import org.videolan.vlc.viewmodels.audio.AudioModel;
import org.videolan.vlc.viewmodels.audio.GenresModel;
import org.videolan.vlc.viewmodels.audio.PlaylistsModel;
import org.videolan.vlc.viewmodels.audio.TracksModel;

import java.util.List;
import java.util.Random;

public class AudioBrowserFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, ViewPager.OnPageChangeListener, TabLayout.OnTabSelectedListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mGenresAdapter;
    private AudioBrowserAdapter mPlaylistAdapter;

    private ArtistModel artistModel;
    private AlbumModel albumModel;
    private TracksModel tracksModel;
    private GenresModel genresModel;
    private PlaylistsModel playlistsModel;

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private final RecyclerView[] mLists = new RecyclerView[MODE_TOTAL];
    private AudioModel[] mProvidersList;
    private FastScroller mFastScroller;
    private SharedPreferences mSettings;

    private static final int SET_REFRESHING = 103;
    private static final int UNSET_REFRESHING = 104;
    private static final int UPDATE_EMPTY_VIEW = 105;
    private final static int MODE_ARTIST = 0;
    private final static int MODE_ALBUM = 1;
    private final static int MODE_SONG = 2;
    private final static int MODE_GENRE = 3;
    private final static int MODE_PLAYLIST = 4;
    private final static int MODE_TOTAL = 5; // Number of audio mProvider modes

    public final static String TAG_ITEM = "ML_ITEM";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mSettings == null) mSettings = PreferenceManager.getDefaultSharedPreferences(getContext());
        setupAdapters(mSettings.getInt(Constants.KEY_AUDIO_CURRENT_TAB, 0));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.audio_browser, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmptyView = view.findViewById(R.id.no_media);
        mViewPager = view.findViewById(R.id.pager);
        mFastScroller = view.findViewById(R.id.songs_fast_scroller);
        mTabLayout = view.findViewById(R.id.sliding_tabs);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        for (int i = 0; i < MODE_TOTAL; i++) mLists[i] = (RecyclerView) mViewPager.getChildAt(i);

        final String[] titles = new String[] {
                getString(R.string.artists),
                getString(R.string.albums),
                getString(R.string.tracks),
                getString(R.string.genres),
                getString(R.string.playlists),
        };
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));
        mViewPager.setCurrentItem(mSettings.getInt(Constants.KEY_AUDIO_CURRENT_TAB, 0));
        final RecyclerView.RecycledViewPool rvp = new RecyclerView.RecycledViewPool();
        for (int i = 0; i< MODE_TOTAL; ++i) {
            final LinearLayoutManager llm = new LinearLayoutManager(getActivity());
            llm.setRecycleChildrenOnDetach(true);
            mLists[i].setLayoutManager(llm);
            mLists[i].setRecycledViewPool(rvp);
            mLists[i].setAdapter(mAdapters[i]);
        }
        mViewPager.setOnTouchListener(mSwipeFilter);
        setupTabLayout();
        mSwipeRefreshLayout.setOnRefreshListener(this);
    }

    private void setupAdapters(final int currentTab) {
        if (mArtistsAdapter == null) {
            mArtistsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ARTIST, this);
            artistModel = ViewModelProviders.of(requireActivity(), new ArtistModel.Factory(mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false))).get(ArtistModel.class);
        }
        if (mAlbumsAdapter == null) {
            mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this);
            albumModel = ViewModelProviders.of(requireActivity()).get(AlbumModel.class);
        }
        if (mSongsAdapter == null) {
            mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this);
            tracksModel = ViewModelProviders.of(requireActivity()).get(TracksModel.class);
        }
        if (mGenresAdapter == null) {
            mGenresAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this);
            genresModel = ViewModelProviders.of(requireActivity()).get(GenresModel.class);
        }
        if (mPlaylistAdapter == null) {
            mPlaylistAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this);
            playlistsModel = ViewModelProviders.of(requireActivity()).get(PlaylistsModel.class);
        }
        mAdapters = new AudioBrowserAdapter[] {mArtistsAdapter, mAlbumsAdapter, mSongsAdapter, mGenresAdapter, mPlaylistAdapter};
        mProvidersList = new AudioModel[] {artistModel, albumModel, tracksModel, genresModel, playlistsModel};
        //Register current tab first
        mProvidersList[currentTab].getSections().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> items) {
                if (items != null) mAdapters[currentTab].update(items);
            }
        });
        for (int i = 0; i < mProvidersList.length; ++i ) {
            if (i == currentTab) continue;
            final int index = i;
            mProvidersList[i].getSections().observe(this, new Observer<List<MediaLibraryItem>>() {
                @Override
                public void onChanged(@Nullable List<MediaLibraryItem> items) {
                    if (items != null) mAdapters[index].update(items);
                }
            });
        }
    }

    private void setupTabLayout() {
        mTabLayout.setupWithViewPager(mViewPager);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        mTabLayout.addOnTabSelectedListener(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (MainActivity) context;
    }

    @Override
    public void onStart() {
        super.onStart();
        mViewPager.addOnPageChangeListener(this);
        mFabPlay.setImageResource(R.drawable.ic_fab_shuffle);
        setFabPlayShuffleAllVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        setSearchVisibility(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        mViewPager.removeOnPageChangeListener(this);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(true);
    }

    @Override
    public void onFabPlayClick(View view) {
        final List<MediaWrapper> list = ((List<MediaWrapper>)(List<?>) mSongsAdapter.getMediaItems());
        final int count = list.size();
        if (count > 0) {
            final Random rand = new Random();
            int randomSong = rand.nextInt(count);
            MediaUtils.openList(getActivity(), list, randomSong, true);
        }
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(enable && mViewPager.getCurrentItem() == MODE_SONG);
    }

    public void setFabPlayShuffleAllVisibility() {
        setFabPlayVisibility(mViewPager.getCurrentItem() == MODE_SONG && mSongsAdapter.getItemCount() > 2);
    }

    /**
     * Handle changes on the list
     */
    private final Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        mActivity.closeSearchView();
        MediaParsingServiceKt.reload(requireContext());
    }

    @Override
    public String getTitle() {
        return getString(R.string.audio);
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    private void updateEmptyView(int position) {
        mEmptyView.setVisibility(getCurrentAdapter().isEmpty() ? View.VISIBLE : View.GONE);
        mEmptyView.setText(position == MODE_PLAYLIST ? R.string.noplaylist : R.string.nomedia);
        setFabPlayShuffleAllVisibility();
    }

    private final TabLayout.TabLayoutOnPageChangeListener tcl = new TabLayout.TabLayoutOnPageChangeListener(mTabLayout);

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        tcl.onPageScrolled(position, positionOffset, positionOffsetPixels);
    }

    @Override
    public void onPageSelected(int position) {
        updateEmptyView(position);
        setFabPlayShuffleAllVisibility();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        getActivity().invalidateOptionsMenu();
        mFastScroller.setRecyclerView(mLists[tab.getPosition()]);
        mSettings.edit().putInt(Constants.KEY_AUDIO_CURRENT_TAB, tab.getPosition()).apply();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        stopActionMode();
        onDestroyActionMode((AudioBrowserAdapter) mLists[tab.getPosition()].getAdapter());
        mActivity.closeSearchView();
        mAdapters[tab.getPosition()].restoreList();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        mLists[tab.getPosition()].smoothScrollToPosition(0);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        tcl.onPageScrollStateChanged(state);
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (item == null) return;
        if (mActionMode != null) {
            super.onClick(v, position, item);
            return;
        }
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            MediaUtils.openMedia(getActivity(), (MediaWrapper) item);
            return;
        }
        final Intent i;
        switch (item.getItemType()) {
            case MediaLibraryItem.TYPE_ARTIST:
            case MediaLibraryItem.TYPE_GENRE:
                i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS);
                i.putExtra(TAG_ITEM, item);
                break;
            case MediaLibraryItem.TYPE_ALBUM:
            case MediaLibraryItem.TYPE_PLAYLIST:
                i = new Intent(getActivity(), PlaylistActivity.class);
                i.putExtra(TAG_ITEM, item);
                break;
            default:
                return;
        }
        startActivity(i);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        super.onUpdateFinished(adapter);
        if (adapter == getCurrentAdapter()) {
            if (!mMediaLibrary.isWorking()) mHandler.sendEmptyMessage(UNSET_REFRESHING);
            mSwipeRefreshLayout.setEnabled(((LinearLayoutManager)getCurrentRV().getLayoutManager()).findFirstVisibleItemPosition() <= 0);
            updateEmptyView(mViewPager.getCurrentItem());
            mFastScroller.setRecyclerView(getCurrentRV());
        }
    }

    @Override
    public AudioModel getViewModel() {
        return mProvidersList[mViewPager.getCurrentItem()];
    }

    public AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) (getCurrentRV()).getAdapter();
    }

    private RecyclerView getCurrentRV() {
        return mLists[mViewPager.getCurrentItem()];
    }

    private static class AudioBrowserHandler extends WeakHandler<AudioBrowserFragment> {
        AudioBrowserHandler(AudioBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            final AudioBrowserFragment fragment = getOwner();
            if (fragment == null) return;
            switch (msg.what) {
                case SET_REFRESHING:
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case UNSET_REFRESHING:
                    removeMessages(SET_REFRESHING);
                    fragment.mSwipeRefreshLayout.setRefreshing(false);
                    break;
                case UPDATE_EMPTY_VIEW:
                    fragment.updateEmptyView(fragment.mViewPager.getCurrentItem());
            }
        }
    }

    public void updateArtists() {
        artistModel.showAll(mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false));
        artistModel.refresh();
    }

    protected boolean playlistModeSelected() {
        return mViewPager.getCurrentItem() == MODE_PLAYLIST;
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
}
