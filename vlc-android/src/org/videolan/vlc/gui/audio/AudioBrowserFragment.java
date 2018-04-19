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

import android.annotation.TargetApi;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaylistActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.audio.AlbumProvider;
import org.videolan.vlc.viewmodels.audio.ArtistProvider;
import org.videolan.vlc.viewmodels.audio.AudioModel;
import org.videolan.vlc.viewmodels.audio.Genresprovider;
import org.videolan.vlc.viewmodels.audio.PlaylistsProvider;
import org.videolan.vlc.viewmodels.audio.TracksProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AudioBrowserFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, ViewPager.OnPageChangeListener, TabLayout.OnTabSelectedListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mGenresAdapter;
    private AudioBrowserAdapter mPlaylistAdapter;

    private ArtistProvider artistProvider;
    private AlbumProvider albumProvider;
    private TracksProvider tracksProvider;
    private Genresprovider genresprovider;
    private PlaylistsProvider playlistsProvider;

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private final ContextMenuRecyclerView[] mLists = new ContextMenuRecyclerView[MODE_TOTAL];
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
        for (int i = 0; i < MODE_TOTAL; i++) mLists[i] = (ContextMenuRecyclerView) mViewPager.getChildAt(i);

        final String[] titles = new String[] {
                getString(R.string.artists),
                getString(R.string.albums),
                getString(R.string.songs),
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
            artistProvider = ViewModelProviders.of(requireActivity(), new ArtistProvider.Factory(mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false))).get(ArtistProvider.class);
        }
        if (mAlbumsAdapter == null) {
            mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this);
            albumProvider = ViewModelProviders.of(requireActivity()).get(AlbumProvider.class);
        }
        if (mSongsAdapter == null) {
            mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this);
            tracksProvider = ViewModelProviders.of(requireActivity()).get(TracksProvider.class);
        }
        if (mGenresAdapter == null) {
            mGenresAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_GENRE, this);
            genresprovider = ViewModelProviders.of(requireActivity()).get(Genresprovider.class);
        }
        if (mPlaylistAdapter == null) {
            mPlaylistAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this);
            playlistsProvider = ViewModelProviders.of(requireActivity()).get(PlaylistsProvider.class);
        }
        mAdapters = new AudioBrowserAdapter[] {mArtistsAdapter, mAlbumsAdapter, mSongsAdapter, mGenresAdapter, mPlaylistAdapter};
        mProvidersList = new AudioModel[] {artistProvider, albumProvider, tracksProvider, genresprovider, playlistsProvider};
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
        for (View rv : mLists) registerForContextMenu(rv);
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
        for (View rv : mLists) unregisterForContextMenu(rv);
        mViewPager.removeOnPageChangeListener(this);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(true);
    }

    protected void setContextMenuItems(Menu menu, int position) {
        final int pos = mViewPager.getCurrentItem();
        if (pos != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (pos == MODE_ARTIST || pos == MODE_GENRE || pos == MODE_ALBUM)
            menu.findItem(R.id.audio_list_browser_play).setVisible(true);
        if (pos != MODE_SONG && pos != MODE_PLAYLIST)
            menu.findItem(R.id.audio_list_browser_delete).setVisible(false);
        else {
            final MenuItem item = menu.findItem(R.id.audio_list_browser_delete);
            final AudioBrowserAdapter adapter = pos == MODE_SONG ? mSongsAdapter : mPlaylistAdapter;
            final MediaLibraryItem mediaItem = adapter.getItem(position);
            if (pos == MODE_PLAYLIST ) item.setVisible(true);
            else {
                String location = ((MediaWrapper)mediaItem).getLocation();
                item.setVisible(FileUtils.canWrite(location));
            }
        }
        if (!AndroidDevices.isPhone) menu.setGroupVisible(R.id.phone_only, false);
    }

    protected boolean handleContextItemSelected(final MenuItem item, final int position) {
        final int mode = mViewPager.getCurrentItem();
        final AudioBrowserAdapter adapter = mAdapters[mode];
        if (position < 0 && position >= adapter.getItemCount()) return false;

        final int id = item.getItemId();
        final MediaLibraryItem mediaItem = adapter.getItem(position);

        if (id == R.id.audio_list_browser_delete) {
            final MediaLibraryItem previous = position > 0 ? adapter.getItem(position-1) : null;
            final MediaLibraryItem next = position < adapter.getItemCount()-1 ? adapter.getItem(position+1) : null;
            final AudioModel provider = getProvider();
            final String message;
            final Runnable action;
            final Runnable cancel;
            final MediaLibraryItem separator = previous != null && previous.getItemType() == MediaLibraryItem.TYPE_DUMMY &&
                    (next == null || next.getItemType() == MediaLibraryItem.TYPE_DUMMY) ? previous : null;
            if (separator != null) provider.remove(separator);
            provider.remove(mediaItem);

            if (mode == MODE_PLAYLIST) {
                cancel = null;
                message = getString(R.string.playlist_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deletePlaylist((Playlist) mediaItem);
                    }
                };
            } else if (mode == MODE_SONG) {
                message = getString(R.string.file_deleted);
                cancel = new Runnable() {
                    @Override
                    public void run() {
                        provider.refresh();
                    }
                };
                action = new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia(mediaItem, true, cancel);
                    }
                };
                if (!checkWritePermission((MediaWrapper) mediaItem, new Runnable() {
                    @Override
                    public void run() {
                        final View v = getView();
                        if (v != null) UiTools.snackerWithCancel(getView(), message, action, cancel);
                    }
                })) return false;
            } else return false;
            final View v = getView();
            if (v != null) UiTools.snackerWithCancel(getView(), message, action, cancel);
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            if (mSongsAdapter.getItemCount() <= position) return false;
            AudioUtil.setRingtone((MediaWrapper) mSongsAdapter.getItem(position), getActivity());
            return true;
        }

        if (id == R.id.audio_view_info) {
            showInfoDialog(mSongsAdapter.getItem(position));
            return true;
        }

        if (id == R.id.audio_view_add_playlist) {
            UiTools.addToPlaylist(getActivity(), mediaItem.getTracks(), SavePlaylistDialog.KEY_NEW_TRACKS);
            return true;
        }

        int startPosition;
        MediaWrapper[] medias;

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;
        boolean insert_next = id == R.id.audio_list_browser_insert_next;

        // Play/Append
        if (useAllItems) {
            if (mSongsAdapter.getItemCount() <= position) return false;
            final List<MediaLibraryItem> mediaList = new ArrayList<>();
            startPosition = mSongsAdapter.getListWithPosition(mediaList, position);
            medias = Arrays.copyOf(mediaList.toArray(), mediaList.size(), MediaWrapper[].class);
        } else {
            startPosition = 0;
            if (position >= adapter.getItemCount()) return false;
            medias = mediaItem.getTracks();
        }

        if (append) MediaUtils.appendMedia(getActivity(), medias);
        else if (insert_next) MediaUtils.insertNext(getActivity(), medias);
        else MediaUtils.openArray(getActivity(), medias, startPosition);
        return true;
    }

    @Override
    public void onFabPlayClick(View view) {
        final List<MediaWrapper> list = ((List<MediaWrapper>)(List<?>) mSongsAdapter.getMediaItems());
        final int count = list.size();
        if (count > 0) {
            Random rand = new Random();
            int randomSong = rand.nextInt(count);
            MediaUtils.openList(getActivity(), list, randomSong, true);
        }
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(enable && mViewPager.getCurrentItem() == MODE_SONG);
    }

    public void setFabPlayShuffleAllVisibility() {
        setFabPlayVisibility(mViewPager.getCurrentItem() == MODE_SONG);
    }

    /**
     * Handle changes on the list
     */
    private final Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        mActivity.closeSearchView();
        VLCApplication.getAppContext().startService(new Intent(Constants.ACTION_RELOAD, null, VLCApplication.getAppContext(), MediaParsingService.class));
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

    private void deletePlaylist(final Playlist playlist) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                playlist.delete();
            }
        });
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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCtxClick(View anchor, final int position, MediaLibraryItem item) {
        if (mActionMode == null)
            getCurrentRV().openContextMenu(position);
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
    public AudioModel getProvider() {
        return mProvidersList[mViewPager.getCurrentItem()];
    }

    public AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) (getCurrentRV()).getAdapter();
    }

    private ContextMenuRecyclerView getCurrentRV() {
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
        artistProvider.showAll(mSettings.getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false));
        artistProvider.refresh();
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
