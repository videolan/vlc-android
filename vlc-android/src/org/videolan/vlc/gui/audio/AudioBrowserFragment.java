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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.PlaylistActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.viewmodels.paged.MLPagedModel;
import org.videolan.vlc.viewmodels.paged.PagedAlbumsModel;
import org.videolan.vlc.viewmodels.paged.PagedArtistsModel;
import org.videolan.vlc.viewmodels.paged.PagedGenresModel;
import org.videolan.vlc.viewmodels.paged.PagedTracksModel;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AudioBrowserFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mGenresAdapter;

    private PagedArtistsModel artistModel;
    private PagedAlbumsModel albumModel;
    private PagedTracksModel tracksModel;
    private PagedGenresModel genresModel;

    private TextView mEmptyView;
    private Button mMedialibrarySettingsBtn;
    private final RecyclerView[] mLists = new RecyclerView[MODE_TOTAL];
    private MLPagedModel<MediaLibraryItem>[] models;
    private SharedPreferences mSettings;
    private FastScroller mFastScroller;



    private static final String KEY_LISTS_POSITIONS = "key_lists_position";
    private static final int SET_REFRESHING = 103;
    private static final int UNSET_REFRESHING = 104;
    private static final int UPDATE_EMPTY_VIEW = 105;
    private final static int MODE_ARTIST = 0;
    private final static int MODE_ALBUM = 1;
    private final static int MODE_SONG = 2;
    private final static int MODE_GENRE = 3;
    private final static int MODE_TOTAL = 4; // Number of audio mProvider modes

    public final static String TAG_ITEM = "ML_ITEM";

    @Override
    protected boolean hasTabs() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mSettings == null) mSettings = Settings.INSTANCE.getInstance(requireContext());
        if (models == null) setupModels();
        if (mSettings.getBoolean("audio_resume_card", true)) ((AudioPlayerContainerActivity)requireActivity()).proposeCard();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.audio_browser, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmptyView = view.findViewById(R.id.no_media);
        mMedialibrarySettingsBtn = view.findViewById(R.id.button_nomedia);
        mFastScroller = view.getRootView().findViewById(R.id.songs_fast_scroller);
        mFastScroller.attachToCoordinator((AppBarLayout) view.getRootView().findViewById(R.id.appbar), (CoordinatorLayout) view.getRootView().findViewById(R.id.coordinator), (FloatingActionButton) view.getRootView().findViewById(R.id.fab));
        mMedialibrarySettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final FragmentActivity activity = requireActivity();
                final Intent intent = new Intent(activity.getApplicationContext(), SecondaryActivity.class);
                intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER);
                startActivity(intent);
                activity.setResult(PreferencesActivity.RESULT_RESTART);
            }
        });
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
        };
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));
        final int tabPosition = mSettings.getInt(Constants.KEY_AUDIO_CURRENT_TAB, 0);
        mViewPager.setCurrentItem(tabPosition);
        final ArrayList<Integer> positions = savedInstanceState != null ? savedInstanceState.getIntegerArrayList(KEY_LISTS_POSITIONS) : null;
        for (int i = 0; i< MODE_TOTAL; ++i) {
            final LinearLayoutManager llm = new LinearLayoutManager(getActivity());
            llm.setRecycleChildrenOnDetach(true);
            mLists[i].setLayoutManager(llm);
            mLists[i].setAdapter(mAdapters[i]);
            if (positions != null) mLists[i].scrollToPosition(positions.get(i));
            mLists[i].addOnScrollListener(mScrollListener);
            mLists[i].addItemDecoration(new RecyclerSectionItemDecoration(getResources().getDimensionPixelSize(R.dimen.recycler_section_header_height), true, models[i]));
        }
        mViewPager.setOnTouchListener(mSwipeFilter);
        mSwipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        final ArrayList<Integer> positions = new ArrayList<>(MODE_TOTAL);
        for (int i = 0; i< MODE_TOTAL; ++i) {
            positions.add(((LinearLayoutManager)mLists[i].getLayoutManager()).findFirstCompletelyVisibleItemPosition());
        }
        outState.putIntegerArrayList(KEY_LISTS_POSITIONS, positions);
        super.onSaveInstanceState(outState);
    }

    private void setupModels() {
        final int current = mSettings.getInt(Constants.KEY_AUDIO_CURRENT_TAB, 0);
        for (int pass = 0 ; pass < 2; ++pass) {
            if ((pass != 0 ^ current == MODE_ARTIST) && mArtistsAdapter == null) {
                artistModel = ViewModelProviders.of(requireActivity(), new PagedArtistsModel.Factory(requireContext(), mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false))).get(PagedArtistsModel.class);
                mArtistsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ARTIST, this);
            }
            if ((pass != 0 ^ current == MODE_ALBUM) && mAlbumsAdapter == null) {
                albumModel = ViewModelProviders.of(requireActivity(), new PagedAlbumsModel.Factory(requireContext(), null)).get(PagedAlbumsModel.class);
                mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this);
            }
            if ((pass != 0 ^ current == MODE_SONG) && mSongsAdapter == null) {
                tracksModel = ViewModelProviders.of(requireActivity(), new PagedTracksModel.Factory(requireContext(), null)).get(PagedTracksModel.class);
                mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this);
            }
            if ((pass != 0 ^ current == MODE_GENRE) && mGenresAdapter == null) {
                genresModel = ViewModelProviders.of(requireActivity(), new PagedGenresModel.Factory(requireContext())).get(PagedGenresModel.class);
                mGenresAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this);
            }
        }
        mAdapters = new AudioBrowserAdapter[] {mArtistsAdapter, mAlbumsAdapter, mSongsAdapter, mGenresAdapter};
        models = new MLPagedModel[] {artistModel, albumModel, tracksModel, genresModel};
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

    @Override
    public void onStart() {
        super.onStart();
        setFabPlayShuffleAllVisibility();
        mFabPlay.setImageResource(R.drawable.ic_fab_shuffle);
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

    private void setFabPlayShuffleAllVisibility() {
        setFabPlayVisibility(mSongsAdapter.getItemCount() > 2);
    }

    /**
     * Handle changes on the list
     */
    private final Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        mActivity.closeSearchView();
        MediaParsingServiceKt.reloadLibrary(requireContext());
    }

    @Override
    public String getTitle() {
        return getString(R.string.audio);
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    private void updateEmptyView() {
        mEmptyView.setVisibility(getCurrentAdapter().isEmpty() ? View.VISIBLE : View.GONE);
        mMedialibrarySettingsBtn.setVisibility(getCurrentAdapter().isEmpty() ? View.VISIBLE : View.GONE);
        setFabPlayShuffleAllVisibility();
    }

    @Override
    public void onPageSelected(int position) {
        updateEmptyView();
        setFabPlayShuffleAllVisibility();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        super.onTabSelected(tab);
        mFastScroller.setRecyclerView(mLists[tab.getPosition()],models[tab.getPosition()]);
        mSettings.edit().putInt(Constants.KEY_AUDIO_CURRENT_TAB, tab.getPosition()).apply();
        final Boolean loading = getViewModel().getLoading().getValue();
        if (loading == null || !loading) mHandler.sendEmptyMessage(UNSET_REFRESHING);
        else mHandler.sendEmptyMessage(SET_REFRESHING);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        super.onTabUnselected(tab);
        onDestroyActionMode((AudioBrowserAdapter) mLists[tab.getPosition()].getAdapter());
        models[tab.getPosition()].restore();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        mLists[tab.getPosition()].smoothScrollToPosition(0);
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
            updateEmptyView();
            mFastScroller.setRecyclerView(getCurrentRV(), getViewModel());
        } else setFabPlayShuffleAllVisibility();
    }

    @Override
    public MLPagedModel<MediaLibraryItem> getViewModel() {
        return models[mViewPager.getCurrentItem()];
    }

    @Override
    public AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) (getCurrentRV()).getAdapter();
    }

    @Override
    protected RecyclerView getCurrentRV() {
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
                    fragment.updateEmptyView();
            }
        }
    }

    public void updateArtists() {
        artistModel.showAll(mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false));
        artistModel.refresh();
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

    @Override
    public boolean allowedToExpand() {
        return getCurrentRV().getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
    }
}
