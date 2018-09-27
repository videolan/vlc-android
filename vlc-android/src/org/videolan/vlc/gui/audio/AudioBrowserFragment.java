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
import android.arch.paging.PagedList;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.viewmodels.paged.MLPagedModel;
import org.videolan.vlc.viewmodels.paged.PagedAlbumsModel;
import org.videolan.vlc.viewmodels.paged.PagedArtistsModel;
import org.videolan.vlc.viewmodels.paged.PagedGenresModel;
import org.videolan.vlc.viewmodels.paged.PagedPlaylistsModel;
import org.videolan.vlc.viewmodels.paged.PagedTracksModel;

public class AudioBrowserFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, ViewPager.OnPageChangeListener, TabLayout.OnTabSelectedListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mGenresAdapter;
    private AudioBrowserAdapter mPlaylistAdapter;

    private PagedArtistsModel artistModel;
    private PagedAlbumsModel albumModel;
    private PagedTracksModel tracksModel;
    private PagedGenresModel genresModel;
    private PagedPlaylistsModel playlistsModel;

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private final RecyclerView[] mLists = new RecyclerView[MODE_TOTAL];
    private MLPagedModel<MediaLibraryItem>[] models;
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
        if (mSettings == null) mSettings = Settings.INSTANCE.getInstance(requireContext());
        setupModels();
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

    private void setupModels() {
        final int current = mSettings.getInt(Constants.KEY_AUDIO_CURRENT_TAB, 0);
        for (int pass = 0 ; pass < 2; ++pass) {
            if ((pass != 0 ^ current == MODE_ARTIST) && mArtistsAdapter == null) {
                artistModel = ViewModelProviders.of(requireActivity(), new PagedArtistsModel.Factory(requireContext(), mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false))).get(PagedArtistsModel.class);
                mArtistsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ARTIST, this, artistModel.getSort());
            }
            if ((pass != 0 ^ current == MODE_ALBUM) && mAlbumsAdapter == null) {
                albumModel = ViewModelProviders.of(requireActivity(), new PagedAlbumsModel.Factory(requireContext(), null)).get(PagedAlbumsModel.class);
                mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this, albumModel.getSort());
            }
            if ((pass != 0 ^ current == MODE_SONG) && mSongsAdapter == null) {
                tracksModel = ViewModelProviders.of(requireActivity(), new PagedTracksModel.Factory(requireContext(), null)).get(PagedTracksModel.class);
                mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, tracksModel.getSort());
            }
            if ((pass != 0 ^ current == MODE_GENRE) && mGenresAdapter == null) {
                genresModel = ViewModelProviders.of(requireActivity(), new PagedGenresModel.Factory(requireContext())).get(PagedGenresModel.class);
                mGenresAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this, genresModel.getSort());
            }
            if ((pass != 0 ^ current == MODE_PLAYLIST) && mPlaylistAdapter == null) {
                playlistsModel = ViewModelProviders.of(requireActivity(), new PagedPlaylistsModel.Factory(requireContext())).get(PagedPlaylistsModel.class);
                mPlaylistAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this, playlistsModel.getSort());
            }
        }
        mAdapters = new AudioBrowserAdapter[] {mArtistsAdapter, mAlbumsAdapter, mSongsAdapter, mGenresAdapter, mPlaylistAdapter};
        models = new MLPagedModel[] {artistModel, albumModel, tracksModel, genresModel, playlistsModel};
        for (int pass = 0 ; pass < 2; ++pass) {
            for (int i = 0; i < models.length; ++i) {
                if (pass == 0 ^ current == i) continue;
                final int index = i;
                models[i].getPagedList().observe(this, new Observer<PagedList<MediaLibraryItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<MediaLibraryItem> items) {
                        if (items != null) mAdapters[index].submitList(items);
                    }
                });
                models[i].getLoading().observe(this, new Observer<Boolean>() {
                    @Override
                    public void onChanged(@Nullable Boolean loading) {
                        if (loading == null || mViewPager.getCurrentItem() != index) return;
                        if (loading) mHandler.sendEmptyMessageDelayed(SET_REFRESHING, 300);
                        else mHandler.sendEmptyMessage(UNSET_REFRESHING);
                    }
                });
            }
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
        MediaUtils.INSTANCE.playAll(view.getContext(), tracksModel, 0, true);
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
        requireActivity().invalidateOptionsMenu();
        mFastScroller.setRecyclerView(mLists[tab.getPosition()], getViewModel().getTotalCount());
        mSettings.edit().putInt(Constants.KEY_AUDIO_CURRENT_TAB, tab.getPosition()).apply();
        final Boolean loading = getViewModel().getLoading().getValue();
        if (loading == null || loading == false) mHandler.sendEmptyMessage(UNSET_REFRESHING);
        else mHandler.sendEmptyMessage(SET_REFRESHING);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        stopActionMode();
        onDestroyActionMode((AudioBrowserAdapter) mLists[tab.getPosition()].getAdapter());
        mActivity.closeSearchView();
        models[tab.getPosition()].restore();
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
            MediaUtils.INSTANCE.openMedia(getActivity(), (MediaWrapper) item);
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
            mSwipeRefreshLayout.setEnabled(((LinearLayoutManager)getCurrentRV().getLayoutManager()).findFirstVisibleItemPosition() <= 0);
            updateEmptyView(mViewPager.getCurrentItem());
            mFastScroller.setRecyclerView(getCurrentRV(), getViewModel().getTotalCount());
        }
    }

    @Override
    public MLPagedModel<MediaLibraryItem> getViewModel() {
        return models[mViewPager.getCurrentItem()];
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
