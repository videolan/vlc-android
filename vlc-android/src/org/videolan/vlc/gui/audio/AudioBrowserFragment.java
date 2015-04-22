/*****************************************************************************
 * AudioBrowserFragment.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import com.android.widget.SlidingTabLayout;

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCRunnable;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioBrowserFragment extends MediaBrowserFragment implements SwipeRefreshLayout.OnRefreshListener, SlidingTabLayout.OnTabChangedListener, MediaBrowser.EventListener, IBrowser {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;
    private MediaBrowser mMediaBrowser;
    private MainActivity mMainActivity;

    List<MediaWrapper> mAudioList;
    private AudioBrowserListAdapter mArtistsAdapter;
    private AudioBrowserListAdapter mAlbumsAdapter;
    private AudioBrowserListAdapter mSongsAdapter;
    private AudioBrowserListAdapter mGenresAdapter;
    private AudioBrowserListAdapter mPlaylistAdapter;
    private ConcurrentLinkedQueue<AudioBrowserListAdapter> mAdaptersToNotify = new ConcurrentLinkedQueue<AudioBrowserListAdapter>();

    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private TextView mEmptyView;
    private List<View> mLists;
    private ImageView mFabPlayShuffleAll;

    public final static int MODE_ARTIST = 0;
    public final static int MODE_ALBUM = 1;
    public final static int MODE_SONG = 2;
    public final static int MODE_GENRE = 3;
    public final static int MODE_PLAYLIST = 4;
    public final static int MODE_TOTAL = 5; // Number of audio browser modes

    public final static int MSG_LOADING = 0;
    private volatile boolean mDisplaying = false;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioBrowserFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance();

        mSongsAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITH_COVER);
        mArtistsAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITH_COVER);
        mAlbumsAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITH_COVER);
        mGenresAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITHOUT_COVER);
        mPlaylistAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITHOUT_COVER);

        mSongsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mArtistsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mAlbumsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mGenresAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mPlaylistAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_browser, container, false);

        mEmptyView = (TextView) v.findViewById(R.id.no_media);

        ListView songsList = (ListView)v.findViewById(R.id.songs_list);
        ListView artistList = (ListView)v.findViewById(R.id.artists_list);
        ListView albumList = (ListView)v.findViewById(R.id.albums_list);
        ListView genreList = (ListView)v.findViewById(R.id.genres_list);
        ListView playlistsList = (ListView)v.findViewById(R.id.playlists_list);

        songsList.setAdapter(mSongsAdapter);
        artistList.setAdapter(mArtistsAdapter);
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setAdapter(mGenresAdapter);
        playlistsList.setAdapter(mPlaylistAdapter);

        mLists = Arrays.asList((View)artistList, albumList, songsList, genreList, playlistsList);
        String[] titles = new String[] {getString(R.string.artists), getString(R.string.albums),
                getString(R.string.songs), getString(R.string.genres), getString(R.string.playlists)};
        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mViewPager.setOnTouchListener(mSwipeFilter);
        mSlidingTabLayout = (SlidingTabLayout) v.findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setCustomTabView(R.layout.tab_layout, R.id.tab_title);
        mSlidingTabLayout.setViewPager(mViewPager);
        mSlidingTabLayout.setOnTabChangedListener(this);

        songsList.setOnItemClickListener(songListener);
        artistList.setOnItemClickListener(artistListListener);
        albumList.setOnItemClickListener(albumListListener);
        genreList.setOnItemClickListener(genreListListener);
        playlistsList.setOnItemClickListener(playlistListener);

        artistList.setOnKeyListener(keyListener);
        albumList.setOnKeyListener(keyListener);
        songsList.setOnKeyListener(keyListener);
        genreList.setOnKeyListener(keyListener);
        playlistsList.setOnKeyListener(keyListener);

        registerForContextMenu(songsList);
        registerForContextMenu(artistList);
        registerForContextMenu(albumList);
        registerForContextMenu(genreList);
        registerForContextMenu(playlistsList);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        songsList.setOnScrollListener(mScrollListener);
        artistList.setOnScrollListener(mScrollListener);
        albumList.setOnScrollListener(mScrollListener);
        genreList.setOnScrollListener(mScrollListener);
        playlistsList.setOnScrollListener(mScrollListener);

        mFabPlayShuffleAll = (ImageView) v.findViewById(R.id.fab_play_shuffle_all);
        mFabPlayShuffleAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabPlayAllClick(v);
            }
        });
        setFabPlayShuffleAllVisibility();

        return v;
    }

    AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener(){
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                boolean enabled = scrollState == SCROLL_STATE_IDLE;
                if (enabled) {
                    enabled = view.getFirstVisiblePosition() == 0;
                    if (view.getChildAt(0) != null)
                        enabled &= view.getChildAt(0).getTop() == 0;
                }
                mSwipeRefreshLayout.setEnabled(enabled);
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {}
    };

    @Override
    public void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);
        mMediaLibrary.setBrowser(null);
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMainActivity = (MainActivity) getActivity();
        if (mMediaLibrary.isWorking())
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);
        else if (mGenresAdapter.isEmpty() || mArtistsAdapter.isEmpty() ||
                mAlbumsAdapter.isEmpty() || mSongsAdapter.isEmpty())
            updateLists();
        else
            focusHelper(false, mLists.get(mViewPager.getCurrentItem()).getId());
        mMediaLibrary.addUpdateHandler(mHandler);
        mMediaLibrary.setBrowser(this);
        final ListView current = (ListView)mLists.get(mViewPager.getCurrentItem());
        current.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setEnabled(current.getFirstVisiblePosition() == 0);
            }
        });
    }

    private void focusHelper(final boolean idIsEmpty, final int listId) {
        final View parent = getView();
        final MainActivity main = (MainActivity)getActivity();
        if (main == null)
            return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                main.setMenuFocusDown(false, R.id.header);
                main.setSearchAsFocusDown(idIsEmpty, parent, listId);
            }
        });
    }

    // Focus support. Start.
    View.OnKeyListener keyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {

            /* Qualify key action to prevent redundant event
             * handling.
             */
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int newPosition = mViewPager.getCurrentItem();

                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (newPosition < (MODE_TOTAL - 1))
                            newPosition++;
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (newPosition > 0)
                            newPosition--;
                        break;
                    default:
                        return false;
                }

                if (newPosition != mViewPager.getCurrentItem()) {
                    ListView vList = (ListView) mLists.get(newPosition);

                    mViewPager.setCurrentItem(newPosition);

                    ((MainActivity)getActivity()).setSearchAsFocusDown(
                            vList.getCount() == 0, getView(),
                            vList.getId());
                }
            }

            // clean up with MainActivity
            return false;
        }
    };
    // Focus support. End.

    OnItemClickListener songListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> mediaLocation = mSongsAdapter.getLocations(p);
            mAudioController.load(mediaLocation, 0);
        }
    };

    OnItemClickListener artistListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mArtistsAdapter.getMedia(p);
            MainActivity activity = (MainActivity)getActivity();
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra("fragment", "albumsSongs");
            i.putParcelableArrayListExtra("list", mediaList);
            i.putExtra("filter", Util.getMediaArtist(activity, mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener albumListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mAlbumsAdapter.getMedia(p);
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra("fragment", SecondaryActivity.ALBUM);
            i.putParcelableArrayListExtra("list", mediaList);
            i.putExtra("filter", Util.getMediaAlbum(getActivity(), mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener genreListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mGenresAdapter.getMedia(p);
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra("fragment", SecondaryActivity.ALBUMS_SONGS);
            i.putParcelableArrayListExtra("list", mediaList);
            i.putExtra("filter", Util.getMediaGenre(getActivity(), mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener playlistListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mPlaylistAdapter.getItem(p).mMediaList;
            if (mediaList.size() == 1) {
                String mediaLocation = mediaList.get(0).getLocation();
                mAudioController.load(mediaLocation, true);
            } else {
                ArrayList<String> mediaLocations = mPlaylistAdapter.getLocations(p);
                mAudioController.load(mediaLocations, 0, true);
            }
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);
        setContextMenuItems(menu, v);
    }

    private void setContextMenuItems(Menu menu, View v) {
        final int pos = mViewPager.getCurrentItem();
        if (pos != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (pos == MODE_ARTIST || pos == MODE_GENRE) {
            MenuItem play = menu.findItem(R.id.audio_list_browser_play);
            play.setVisible(true);
        }
        if (pos != MODE_SONG && pos != MODE_PLAYLIST)
            menu.findItem(R.id.audio_list_browser_delete).setVisible(false);
        if (!AndroidDevices.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if(!getUserVisibleHint())
            return super.onContextItemSelected(menu);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menu.getMenuInfo();
        if (info != null && handleContextItemSelected(menu, info.position))
            return true;
        return super.onContextItemSelected(menu);
    }

    private boolean handleContextItemSelected(MenuItem item, int position) {
        ContextMenuInfo menuInfo = item.getMenuInfo();

        int startPosition;
        int groupPosition;
        int mode = mViewPager.getCurrentItem();
        List<String> medias;
        int id = item.getItemId();

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;

        if (ExpandableListContextMenuInfo.class.isInstance(menuInfo)) {
            ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
            groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        }
        else
            groupPosition = position;

        if (id == R.id.audio_list_browser_delete) {
            AudioBrowserListAdapter adapter;
            if (mode == MODE_SONG){
                adapter = mSongsAdapter;
            } else if (mode == MODE_PLAYLIST) {
                adapter = mPlaylistAdapter;
            } else
                return false;
            if (adapter.getCount() <= groupPosition || adapter.getLocations(groupPosition) == null || adapter.getLocations(groupPosition).isEmpty())
                return false;
            AlertDialog alertDialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    adapter.getLocations(groupPosition).get(0),
                    adapter.getItem(groupPosition).mTitle,
                    new VLCRunnable(adapter.getItem(groupPosition)) {
                        @Override
                        public void run(Object o) {
                            AudioBrowserListAdapter.ListItem listItem = (AudioBrowserListAdapter.ListItem)o;
                            if (!MediaDatabase.getInstance().playlistExists(listItem.mTitle)) {
                                MediaWrapper media = listItem.mMediaList.get(0);
                                mMediaLibrary.getMediaItems().remove(media);
                                if (mAudioController.getMediaLocations().contains(media.getLocation()))
                                    mAudioController.removeLocation(media.getLocation());
                            } else {
                                MediaDatabase.getInstance().playlistDelete(listItem.mTitle);
                            }
                            updateLists();
                        }
                    });
            alertDialog.show();
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            if (mSongsAdapter.getCount() <= groupPosition)
                return false;
            AudioUtil.setRingtone(mSongsAdapter.getItem(groupPosition).mMediaList.get(0), getActivity());
            return true;
        }

        if (useAllItems) {
            if (mSongsAdapter.getCount() <= groupPosition)
                return false;
            medias = new ArrayList<String>();
            startPosition = mSongsAdapter.getListWithPosition(medias, groupPosition);
        }
        else {
            startPosition = 0;
            AudioBrowserListAdapter adapter = null;
            switch (mode){
                case MODE_SONG:
                    adapter = mSongsAdapter;
                    break;
                case MODE_ARTIST:
                    adapter = mArtistsAdapter;
                    break;
                case MODE_ALBUM:
                    adapter = mAlbumsAdapter;
                    break;
                case MODE_GENRE:
                    adapter = mGenresAdapter;
                    break;
                case MODE_PLAYLIST: //For file playlist, we browse tracks with mediabrowser, and add them in callbacks onMediaAdded and onBrowseEnd
                    medias = mPlaylistAdapter.getLocations(groupPosition);
                    if (medias.size() <2) {
                        if (mMediaBrowser == null)
                            mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
                        mMediaBrowser.browse(mPlaylistAdapter.getMedia(groupPosition).get(0).getLocation());
                        return true;
                    }
                    break;
                default:
                    return true;
            }
            if (groupPosition >= adapter.getCount())
                return false;
            medias = adapter.getLocations(groupPosition);
        }

        if (append)
            mAudioController.append(medias);
        else
            mAudioController.load(medias, startPosition);

        return super.onContextItemSelected(item);
    }

    public void onFabPlayAllClick(View view) {
        List<String> medias = new ArrayList<String>();
        mSongsAdapter.getListWithPosition(medias, 0);
        if (mSongsAdapter.getCount() > 0) {
            Random rand = new Random();
            int randomSong = rand.nextInt(mSongsAdapter.getCount());
            mAudioController.load(medias, randomSong);
            mAudioController.shuffle();
        }
    }

    public void setFabPlayShuffleAllVisibility() {
        if (mViewPager.getCurrentItem() == MODE_SONG)
            mFabPlayShuffleAll.setVisibility(View.VISIBLE);
        else
            mFabPlayShuffleAll.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        if (!MediaLibrary.getInstance().isWorking())
            MediaLibrary.getInstance().loadMediaItems(getActivity(), true);
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (mAdaptersToNotify == null || mAdaptersToNotify.isEmpty())
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
                    for (AudioBrowserListAdapter adapter : mAdaptersToNotify)
                        adapter.notifyDataSetChanged();
                    mAdaptersToNotify.clear();

                    // Refresh the fast scroll data, since SectionIndexer doesn't respect notifyDataSetChanged
                    if (getView() != null) {
                        for (View v : mLists)
                            ((ListView)v).setFastScrollEnabled(true);
                    }
                    focusHelper(false, R.id.artists_list);
                    mHandler.removeMessages(MSG_LOADING);
                    mSwipeRefreshLayout.setRefreshing(false);
                    mDisplaying = false;
                    updateEmptyView(mViewPager.getCurrentItem());
                }
            });
    }

    @Override
    protected String getTitle() {
        return getString(R.string.audio);
    }

    @Override
    public void tabChanged(int position) {
        updateEmptyView(position);
        setFabPlayShuffleAllVisibility();
    }

    private void updateEmptyView(int position) {
        if (position == MODE_PLAYLIST){
            mEmptyView.setVisibility(mPlaylistAdapter.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.noplaylist);
        } else {
            mEmptyView.setVisibility(mAudioList == null || mAudioList.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.nomedia);
        }
    }

    ArrayList<String> mLocationsToAppend = new ArrayList<String>(); //Playlist tracks to append

    @Override
    public void onMediaAdded(int index, Media media) {
        mLocationsToAppend.add(media.getMrl());
    }

    @Override
    public void onMediaRemoved(int index, Media media) {}

    @Override
    public void onBrowseEnd() {
        mAudioController.append(mLocationsToAppend);
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

    private static class AudioBrowserHandler extends WeakHandler<AudioBrowserFragment> {
        public AudioBrowserHandler(AudioBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioBrowserFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                    fragment.updateLists();
                    break;
                case MSG_LOADING:
                    if (fragment.mArtistsAdapter.isEmpty() && fragment.mAlbumsAdapter.isEmpty() &&
                            fragment.mSongsAdapter.isEmpty() && fragment.mGenresAdapter.isEmpty())
                        fragment.mSwipeRefreshLayout.setRefreshing(true);
            }
        }
    };

    private void updateLists() {
        clear();
        mAudioList = MediaLibrary.getInstance().getAudioItems();
        if (mAudioList.isEmpty()){
            updateEmptyView(mViewPager.getCurrentItem());
            mSwipeRefreshLayout.setRefreshing(false);
            mSlidingTabLayout.setVisibility(View.GONE);
            focusHelper(true, R.id.artists_list);
        } else {
            mSlidingTabLayout.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);

            ExecutorService tpe = Executors.newSingleThreadExecutor();
            ArrayList<Runnable> tasks = new ArrayList<Runnable>(Arrays.asList(updateArtists,
                    updateAlbums, updateSongs, updateGenres, updatePlaylists));

            //process the visible list first
            tasks.add(0, tasks.remove(mViewPager.getCurrentItem()));
            tasks.add(new Runnable() {
                @Override
                public void run() {
                    if (!mAdaptersToNotify.isEmpty())
                        display();
                }
            });
            for (Runnable task : tasks)
                tpe.submit(task);
        }
    }

    Runnable updateArtists = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byArtist);
            mArtistsAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_ARTISTS);
            mAdaptersToNotify.add(mArtistsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateAlbums = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byAlbum);
            mAlbumsAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_ALBUMS);
            mAdaptersToNotify.add(mAlbumsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateSongs = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byName);
            mSongsAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_SONGS);
            mAdaptersToNotify.add(mSongsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateGenres = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byGenre);
            mGenresAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_GENRES);
            mAdaptersToNotify.add(mGenresAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updatePlaylists = new Runnable() {
        @Override
        public void run() {
            //File playlists
            ArrayList<MediaWrapper> playlists = mMediaLibrary.getPlaylistFilesItems();
            mPlaylistAdapter.addAll(playlists, AudioBrowserListAdapter.TYPE_PLAYLISTS);
            //DB playlists
            ArrayList<AudioBrowserListAdapter.ListItem> dbPlaylists = mMediaLibrary.getPlaylistDbItems();
            mPlaylistAdapter.addAll(dbPlaylists);

            mAdaptersToNotify.add(mPlaylistAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    AudioBrowserListAdapter.ContextPopupMenuListener mContextPopupMenuListener
        = new AudioBrowserListAdapter.ContextPopupMenuListener() {

            @Override
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            public void onPopupMenu(View anchor, final int position) {
                if (!LibVlcUtil.isHoneycombOrLater()) {
                    // Call the "classic" context menu
                    anchor.performLongClick();
                    return;
                }

                PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
                popupMenu.getMenuInflater().inflate(R.menu.audio_list_browser, popupMenu.getMenu());
                setContextMenuItems(popupMenu.getMenu(), anchor);

                popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return handleContextItemSelected(item, position);
                    }
                });
                popupMenu.show();
            }

    };

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
}
