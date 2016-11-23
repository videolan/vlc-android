/*****************************************************************************
 * AudioBrowserFragment.java
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.Filter;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.NpaLinearLayoutManager;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioBrowserFragment extends BaseAudioBrowser implements DevicesDiscoveryCb, SwipeRefreshLayout.OnRefreshListener, MediaBrowser.EventListener, IBrowser, ViewPager.OnPageChangeListener, AudioBrowserAdapter.EventsHandler, Medialibrary.ArtistsAddedCb, Medialibrary.ArtistsModifiedCb, Medialibrary.AlbumsAddedCb, Medialibrary.AlbumsModifiedCb, MediaAddedCb, MediaUpdatedCb, TabLayout.OnTabSelectedListener, Filterable {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private MediaBrowser mMediaBrowser;
    private MainActivity mMainActivity;

    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mGenresAdapter;
    private AudioBrowserAdapter mPlaylistAdapter;
    private ConcurrentLinkedQueue<AudioBrowserAdapter> mAdaptersToNotify = new ConcurrentLinkedQueue<>();

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private List<View> mLists;
    private FastScroller mFastScroller;
    private FloatingActionButton mFabPlayShuffleAll;
    private View mSearchButtonView;

    public static final int REFRESH = 101;
    public static final int UPDATE_LIST = 102;
    public static final int SET_REFRESHING = 103;
    public static final int UNSET_REFRESHING = 104;
    private final static int MODE_ARTIST = 0;
    private final static int MODE_ALBUM = 1;
    private final static int MODE_SONG = 2;
    private final static int MODE_GENRE = 3;
    private final static int MODE_PLAYLIST = 4;
    private final static int MODE_TOTAL = 5; // Number of audio browser modes

    public final static int MSG_LOADING = 0;
    private volatile boolean mDisplaying = false;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioBrowserFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSongsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mArtistsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mAlbumsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mGenresAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mPlaylistAdapter = new AudioBrowserAdapter(getActivity(), this, true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_browser, container, false);

        mEmptyView = (TextView) v.findViewById(R.id.no_media);

        ContextMenuRecyclerView songsList = (ContextMenuRecyclerView)v.findViewById(R.id.songs_list);
        ContextMenuRecyclerView artistList = (ContextMenuRecyclerView)v.findViewById(R.id.artists_list);
        ContextMenuRecyclerView albumList = (ContextMenuRecyclerView)v.findViewById(R.id.albums_list);
        ContextMenuRecyclerView genreList = (ContextMenuRecyclerView)v.findViewById(R.id.genres_list);
        ContextMenuRecyclerView playlistsList = (ContextMenuRecyclerView)v.findViewById(R.id.playlists_list);

        mFastScroller = (FastScroller) v.findViewById(R.id.songs_fast_scroller);

        mLists = Arrays.asList((View)artistList, albumList, songsList, genreList, playlistsList);

        songsList.setAdapter(mSongsAdapter);
        artistList.setAdapter(mArtistsAdapter);
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setAdapter(mGenresAdapter);
        playlistsList.setAdapter(mPlaylistAdapter);

        String[] titles = new String[] {getString(R.string.artists), getString(R.string.albums),
                getString(R.string.songs), getString(R.string.genres), getString(R.string.playlists)};
        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        setupTabLayout();

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSearchButtonView = v.findViewById(R.id.searchButton);

        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        for (View rv : mLists) {
            ((RecyclerView) rv).setLayoutManager(new NpaLinearLayoutManager(getActivity()));
            registerForContextMenu(rv);
        }

        mViewPager.setOnTouchListener(mSwipeFilter);
        mFastScroller.setRecyclerView(getCurrentRV());
    }

    public void onStart() {
        super.onStart();
        mFabPlayShuffleAll = (FloatingActionButton)getActivity().findViewById(R.id.fab);
        mFabPlayShuffleAll.setImageResource(R.drawable.ic_fab_shuffle);
        setFabPlayShuffleAllVisibility();
        mFabPlayShuffleAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabPlayAllClick(v);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mFabPlayShuffleAll.setOnClickListener(null);
        mFabPlayShuffleAll.setVisibility(View.INVISIBLE);
    }

    private void setupTabLayout() {
        mTabLayout.setupWithViewPager(mViewPager);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        mTabLayout.addOnTabSelectedListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        mViewPager.removeOnPageChangeListener(this);
        mMediaLibrary.removeDeviceDiscoveryCb(this);
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mMainActivity = (MainActivity) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        setSearchVisibility(false);
        mViewPager.addOnPageChangeListener(this);
        mMediaLibrary.addDeviceDiscoveryCb(this);
        mMediaLibrary.setArtistsAddedCb(this);
        mMediaLibrary.setAlbumsAddedCb(this);
        mMediaLibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_AUDIO_EMPTY);
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_AUDIO_EMPTY);
        if (mGenresAdapter.isEmpty() || mArtistsAdapter.isEmpty() ||
                mAlbumsAdapter.isEmpty() || mSongsAdapter.isEmpty())
            updateLists();
        else {
            updateEmptyView(mViewPager.getCurrentItem());
            updatePlaylists();
        }
        final RecyclerView current = getCurrentRV();
        current.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setEnabled(((LinearLayoutManager)current.getLayoutManager()).findFirstVisibleItemPosition() == 0);
                }
            });
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
            MenuItem item = menu.findItem(R.id.audio_list_browser_delete);
            AudioBrowserAdapter adapter = pos == MODE_SONG ? mSongsAdapter : mPlaylistAdapter;
            MediaLibraryItem mediaItem = adapter.getItem(position);
            if (pos == MODE_PLAYLIST )
                item.setVisible(true);
            else if (pos == MODE_SONG){
                String location = ((MediaWrapper)mediaItem).getLocation();
                item.setVisible(FileUtils.canWrite(location));
            }
        }
        if (!AndroidDevices.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
    }

    protected boolean handleContextItemSelected(final MenuItem item, final int position) {
        final AudioBrowserAdapter adapter;
        int mode = mViewPager.getCurrentItem();
        switch (mode) {
            case MODE_SONG:
                adapter = mSongsAdapter;
                break;
            case MODE_ALBUM:
                adapter = mAlbumsAdapter;
                break;
            case MODE_ARTIST:
                adapter = mArtistsAdapter;
                break;
            case MODE_PLAYLIST:
                adapter = mPlaylistAdapter;
                break;
            case MODE_GENRE:
                adapter = mGenresAdapter;
                break;
            default:
                return false;
        }
        if (position < 0 && position >= adapter.getItemCount())
            return false;

        int id = item.getItemId();
        MediaLibraryItem mediaItem = adapter.getItem(position);

        if (id == R.id.audio_list_browser_delete) {
            final MediaLibraryItem mediaLibraryItem = adapter.getItem(position);
            final MediaLibraryItem previous = position> 0 ? adapter.getItem(position-1) : null;
            final MediaLibraryItem next = position < adapter.getItemCount() ? adapter.getItem(position+1) : null;
            String message;
            Runnable action;
            final MediaLibraryItem separator = previous != null && previous.getItemType() == MediaLibraryItem.TYPE_DUMMY &&
                    (next == null || next.getItemType() == MediaLibraryItem.TYPE_DUMMY) ? adapter.getItem(position-1) : null;
            adapter.remove(position);
            if (separator != null)
                adapter.remove(position-1);

            if (mode == MODE_PLAYLIST) {
                message = getString(R.string.playlist_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deletePlaylist((Playlist) mediaLibraryItem);
                    }
                };
            } else if (mode == MODE_SONG) {
                message = getString(R.string.file_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia(mediaLibraryItem, true);
                    }
                };
            } else
                return false;
            UiTools.snackerWithCancel(getView(), message, action, new Runnable() {
                @Override
                public void run() {
                    if (separator != null)
                        adapter.addItem(position-1, separator);
                    adapter.addItem(position, mediaLibraryItem);
                }
            });
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            if (mSongsAdapter.getItemCount() <= position)
                return false;
            AudioUtil.setRingtone((MediaWrapper) mSongsAdapter.getItem(position), getActivity());
            return true;
        }

        if (id == R.id.audio_view_info) {
            showInfoDialog((MediaWrapper) mSongsAdapter.getItem(position));
            return true;
        }

        if (id == R.id.audio_view_add_playlist) {
            UiTools.addToPlaylist(getActivity(), new ArrayList<>(Arrays.asList(mediaItem.getTracks(mMediaLibrary))));
            return true;
        }

        int startPosition;
        MediaWrapper[] medias;

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;

        // Play/Append
        if (useAllItems) {
            if (mSongsAdapter.getItemCount() <= position)
                return false;
            ArrayList<MediaLibraryItem> mediaList = new ArrayList<>();
            startPosition = mSongsAdapter.getListWithPosition(mediaList, position);
            medias = Arrays.copyOf(mediaList.toArray(), mediaList.size(), MediaWrapper[].class);
        } else {
            startPosition = 0;
            if (position >= adapter.getItemCount())
                return false;
            medias = mediaItem.getTracks(mMediaLibrary);
        }

        if (mService != null) {
            if (append)
                mService.append(medias);
            else
                mService.load(medias, startPosition);
            return true;
        } else
            return false;
    }

    public void onFabPlayAllClick(View view) {
        List<MediaWrapper> list = ((List<MediaWrapper>)(List<?>) mSongsAdapter.getMediaItems());
        int count = list.size();
        if (count > 0) {
            Random rand = new Random();
            int randomSong = rand.nextInt(count);
            if (mService != null) {
                mService.load(list, randomSong);
                mService.shuffle();
            }
        }
    }

    public void setFabPlayShuffleAllVisibility() {
        mFabPlayShuffleAll.setVisibility(mViewPager.getCurrentItem() == MODE_SONG ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        mMainActivity.closeSearchView();
        if (!mMediaLibrary.isWorking()) {
            updateLists();
        }
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (Util.isListEmpty(mAdaptersToNotify))
            mReadyToDisplay = ready;
        else
            display();
    }

    @Override
    public void display() {
        mReadyToDisplay = true;
        if (mAdaptersToNotify.isEmpty())
            return;
        mDisplaying = true;
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (AudioBrowserAdapter adapter : mAdaptersToNotify)
                        adapter.notifyDataSetChanged();
                    mAdaptersToNotify.clear();
                    mHandler.removeMessages(MSG_LOADING);
                    mSwipeRefreshLayout.setRefreshing(false);
                    mDisplaying = false;
                    updateEmptyView(mViewPager.getCurrentItem());
                    mFastScroller.setRecyclerView(getCurrentRV());
                }
            });
    }

    @Override
    protected String getTitle() {
        return getString(R.string.audio);
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    public Filter getFilter() {
        return getCurrentAdapter().getFilter();
    }

    public void restoreList() {
       getCurrentAdapter().restoreList();
    }

    @Override
    public void setSearchVisibility(boolean visible) {
        mSearchButtonView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyView(int position) {
        mEmptyView.setVisibility(getCurrentAdapter().isEmpty() ? View.VISIBLE : View.GONE);
        mEmptyView.setText(position == MODE_PLAYLIST ? R.string.noplaylist : R.string.nomedia);
    }

    ArrayList<MediaWrapper> mTracksToAppend = new ArrayList<MediaWrapper>(); //Playlist tracks to append

    @Override
    public void onMediaAdded(int index, Media media) {
        mTracksToAppend.add(new MediaWrapper(media));
    }

    @Override
    public void onMediaRemoved(int index, Media media) {}

    @Override
    public void onBrowseEnd() {
        if (mService != null)
            mService.append(mTracksToAppend);
    }

    @Override
    public void showProgressBar() {
        mMainActivity.showProgressBar();
    }

    @Override
    public void hideProgressBar() {
        mMainActivity.hideProgressBar();
    }

    @Override
    public void clearTextInfo() {
        mMainActivity.clearTextInfo();
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        mMainActivity.sendTextInfo(info, progress, max);
    }

    TabLayout.TabLayoutOnPageChangeListener tcl = new TabLayout.TabLayoutOnPageChangeListener(mTabLayout);

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
        mFastScroller.setRecyclerView((RecyclerView) mLists.get(tab.getPosition()));
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        stopActionMode();
        mMainActivity.closeSearchView();
        ((AudioBrowserAdapter)((RecyclerView)mLists.get(tab.getPosition())).getAdapter()).restoreList();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        ((RecyclerView) mLists.get(tab.getPosition())).smoothScrollToPosition(0);
    }

    private void deletePlaylist(final Playlist playlist) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                playlist.delete(mMediaLibrary);
                mHandler.obtainMessage(UPDATE_LIST).sendToTarget();
            }
        });
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        tcl.onPageScrollStateChanged(state);
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (item.getItemType() == MediaLibraryItem.TYPE_ARTIST || item.getItemType() == MediaLibraryItem.TYPE_GENRE) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS);
            i.putExtra(AudioAlbumsSongsFragment.TAG_ITEM, item);
            startActivity(i);
        } else if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            mService.load((MediaWrapper) item);
        } else if (item.getItemType() == MediaLibraryItem.TYPE_ALBUM) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUM);
            i.putExtra(AudioAlbumFragment.TAG_ITEM, item);
            startActivity(i);
        } else if (item.getItemType() == MediaLibraryItem.TYPE_PLAYLIST) {
            mService.load(item.getTracks(mMediaLibrary), 0);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCtxClick(View anchor, final int position, MediaLibraryItem item) {
        getCurrentRV().openContextMenu(position);
    }

    @Override
    public void onArtistsAdded() {
        updateLists();
    }

    @Override
    public void onArtistsModified() {
        updateLists();
    }

    @Override
    public void onAlbumsAdded() {
        updateLists();
    }

    @Override
    public void onAlbumsModified() {
        updateLists();
    }

    @Override
    public void onMediaAdded(MediaWrapper[] mediaList) {
        updateLists();
    }

    @Override
    public void onMediaUpdated(MediaWrapper[] mediaList) {
        updateLists();
    }

    protected AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) (getCurrentRV()).getAdapter();
    }

    private ContextMenuRecyclerView getCurrentRV() {
        return (ContextMenuRecyclerView)mLists.get(mViewPager.getCurrentItem());
    }

    private static class AudioBrowserHandler extends WeakHandler<AudioBrowserFragment> {
    AudioBrowserHandler(AudioBrowserFragment owner) {
        super(owner);
    }

    @Override
    public void handleMessage(Message msg) {
        final AudioBrowserFragment fragment = getOwner();
        if(fragment == null) return;

        switch (msg.what) {
            case MSG_LOADING:
                if (fragment.mArtistsAdapter.isEmpty() && fragment.mAlbumsAdapter.isEmpty() &&
                        fragment.mSongsAdapter.isEmpty() && fragment.mGenresAdapter.isEmpty())
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                break;
            case REFRESH:
                refresh(fragment, (String) msg.obj);
                break;
            case UPDATE_LIST:
                fragment.updateLists();
                break;
            case SET_REFRESHING:
                fragment.mSwipeRefreshLayout.setRefreshing(true);
                break;
            case UNSET_REFRESHING:
                fragment.mSwipeRefreshLayout.setRefreshing(false);
                break;
        }
    }

    private void refresh(AudioBrowserFragment fragment, String path) {
        if (fragment.mService == null)
            return;

        final List<String> mediaLocations = fragment.mService.getMediaLocations();
        if (mediaLocations != null && mediaLocations.contains(path))
            fragment.mService.removeLocation(path);
        fragment.updateLists();
    }
}

    @MainThread
    private void updateLists() {
            mTabLayout.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);
            final ArrayList<Runnable> tasks = new ArrayList<Runnable>(Arrays.asList(updateArtists,
                    updateAlbums, updateSongs, updateGenres, updatePlaylists));

            //process the visible list first
            if (mViewPager.getCurrentItem() != 0)
                tasks.add(0, tasks.remove(mViewPager.getCurrentItem()));
            Runnable displayRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!mAdaptersToNotify.isEmpty() && mReadyToDisplay && !mDisplaying)
                            display();
                }
            };
            //Update display once current tab items are fetched, then update for other lists
            tasks.add(1, displayRunnable);
            tasks.add(displayRunnable);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    for (Runnable task : tasks)
                        task.run();
                }
            });
    }

    Runnable updateArtists = new Runnable() {
        @Override
        public void run() {
            mArtistsAdapter.addAll(mMediaLibrary.getArtists());
            mAdaptersToNotify.add(mArtistsAdapter);
        }
    };

    Runnable updateAlbums = new Runnable() {
        @Override
        public void run() {
            mAlbumsAdapter.addAll(mMediaLibrary.getAlbums());
            mAdaptersToNotify.add(mAlbumsAdapter);
        }
    };

    Runnable updateSongs = new Runnable() {
        @Override
        public void run() {
            mSongsAdapter.addAll(mMediaLibrary.getAudio());
            mAdaptersToNotify.add(mSongsAdapter);
        }
    };

    Runnable updateGenres = new Runnable() {
        @Override
        public void run() {
            mGenresAdapter.addAll(mMediaLibrary.getGenres());
            mAdaptersToNotify.add(mGenresAdapter);
        }
    };

    Runnable updatePlaylists = new Runnable() {
        @Override
        public void run() {
            //DB playlists
            mPlaylistAdapter.addAll(mMediaLibrary.getPlaylists());
            mAdaptersToNotify.add(mPlaylistAdapter);
        }
    };

    private void updatePlaylists() {
        VLCApplication.runBackground(updatePlaylists);
    }

    protected boolean songModeSelected() {
        return mViewPager.getCurrentItem() == MODE_SONG;
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
            mSwipeRefreshLayout.setEnabled(false);
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    mSwipeRefreshLayout.setEnabled(true);
                    break;
            }
            return false;
        }
    };

    public void clear(){
        mGenresAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mSongsAdapter.clear();
        mPlaylistAdapter.clear();
    }

    boolean mParsing = false;
    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        if (!mParsing && mSwipeRefreshLayout.isRefreshing())
            mHandler.sendEmptyMessage(UPDATE_LIST);
    }

    @Override
    public void onParsingStatsUpdated(int percent) {
        mParsing = percent < 100;
        if (percent == 100) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateLists();
                }
            });
            hideProgressBar();
        } else if (!mSwipeRefreshLayout.isRefreshing())
            mHandler.sendEmptyMessage(SET_REFRESHING);
    }
}
