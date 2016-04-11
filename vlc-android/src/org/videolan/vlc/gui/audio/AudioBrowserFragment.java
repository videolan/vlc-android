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
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
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
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioBrowserFragment extends MediaBrowserFragment implements SwipeRefreshLayout.OnRefreshListener, MediaBrowser.EventListener, IBrowser, ViewPager.OnPageChangeListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

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
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private List<View> mLists;
    private FloatingActionButton mFabPlayShuffleAll;

    public static final int REFRESH = 101;
    public static final int UPDATE_LIST = 102;
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

        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        setupTabLayout();

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

        mFabPlayShuffleAll = (FloatingActionButton)v.findViewById(R.id.fab_play_shuffle_all);
        mFabPlayShuffleAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabPlayAllClick(v);
            }
        });
        setFabPlayShuffleAllVisibility();

        return v;
    }

    private void setupTabLayout() {
        final PagerAdapter adapter = mViewPager.getAdapter();
        mTabLayout.setTabsFromPagerAdapter(adapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                ((ListView) mLists.get(tab.getPosition())).smoothScrollToPosition(0);
            }
        });
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

        mViewPager.removeOnPageChangeListener(this);
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

        mViewPager.addOnPageChangeListener(this);
        if (mMediaLibrary.isWorking())
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);
        else if (mGenresAdapter.isEmpty() || mArtistsAdapter.isEmpty() ||
                mAlbumsAdapter.isEmpty() || mSongsAdapter.isEmpty())
            updateLists();
        else {
            updateEmptyView(mViewPager.getCurrentItem());
        }
        mMediaLibrary.addUpdateHandler(mHandler);
        mMediaLibrary.setBrowser(this);
        final ListView current = (ListView)mLists.get(mViewPager.getCurrentItem());
        current.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setEnabled(current.getFirstVisiblePosition() == 0);
            }
        });
        updatePlaylists();
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
            if (mService != null)
                mService.load(mSongsAdapter.getMedias(p), 0);
        }
    };

    OnItemClickListener artistListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mArtistsAdapter.getMedias(p);
            if (mediaList.isEmpty())
                return;
            MainActivity activity = (MainActivity)getActivity();
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS);
            VLCApplication.storeData(SecondaryActivity.ALBUMS_SONGS, mediaList);
            i.putExtra(SecondaryActivity.KEY_FILTER, MediaUtils.getMediaArtist(activity, mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener albumListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mAlbumsAdapter.getMedias(p);
            if (mediaList.isEmpty())
                return;
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUM);
            VLCApplication.storeData(SecondaryActivity.ALBUM, mediaList);
            i.putExtra(SecondaryActivity.KEY_FILTER, MediaUtils.getMediaAlbum(getActivity(), mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener genreListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mGenresAdapter.getMedias(p);
            if (mediaList.isEmpty())
                return;
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS);
            VLCApplication.storeData(SecondaryActivity.ALBUMS_SONGS, mediaList);
            i.putExtra(SecondaryActivity.KEY_FILTER, MediaUtils.getMediaGenre(getActivity(), mediaList.get(0)));
            startActivity(i);
        }
    };

    OnItemClickListener playlistListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            loadPlaylist(p);
        }
    };

    private void loadPlaylist(int position) {
        ArrayList<MediaWrapper> mediaList = mPlaylistAdapter.getItem(position).mMediaList;
        if (mService == null)
            return;
        if (mediaList.size() == 1 && mediaList.get(0).getType() == MediaWrapper.TYPE_PLAYLIST) {
            mService.load(mediaList.get(0));
        } else {
            mService.load(mPlaylistAdapter.getMedias(position), 0);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);

        int position = ((AdapterContextMenuInfo) menuInfo).position;
        setContextMenuItems(menu, position);
    }

    private void setContextMenuItems(Menu menu, int position) {
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
            AudioBrowserListAdapter adapter = pos == MODE_SONG ? mSongsAdapter : mPlaylistAdapter;
            AudioBrowserListAdapter.ListItem mediaItem = adapter.getItem(position);
            if (pos == MODE_PLAYLIST && MediaDatabase.getInstance().playlistExists(mediaItem.mTitle))
                item.setVisible(true);
            else {
                String location = mediaItem.mMediaList.get(0).getLocation();
                item.setVisible(FileUtils.canWrite(location));
            }
        }
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

    private boolean handleContextItemSelected(final MenuItem item, final int position) {
        final AudioBrowserListAdapter adapter;
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
        if (position < 0 && position >= adapter.getCount())
            return false;

        int id = item.getItemId();

        if (id == R.id.audio_list_browser_delete) {
            List<MediaWrapper> mediaList = adapter.getMedias(position);
            if (mediaList == null || mediaList.isEmpty())
                return false;
            final MediaWrapper media = mediaList.get(0);
            final AudioBrowserListAdapter.ListItem listItem = adapter.getItem(position);
            final String key = adapter.getKey(position);
            String message;
            Runnable action;

            adapter.remove(position, key);

            if (mode == MODE_PLAYLIST) {
                message = getString(R.string.playlist_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deletePlaylist(listItem);
                    }
                };
            } else {
                message = getString(R.string.file_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia(media);
                    }
                };
            }
            UiTools.snackerWithCancel(getView(), message, action, new Runnable() {
                @Override
                public void run() {
                    adapter.addItem(position, key, listItem);
                }
            });
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            if (mSongsAdapter.getCount() <= position)
                return false;
            AudioUtil.setRingtone(mSongsAdapter.getItem(position).mMediaList.get(0), getActivity());
            return true;
        }

        if (id == R.id.audio_view_info) {
                Intent i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra("fragment", "mediaInfo");
                i.putExtra("param", mSongsAdapter.getItem(position).mMediaList.get(0).getUri().toString());
                getActivity().startActivityForResult(i, MainActivity.ACTIVITY_RESULT_SECONDARY);
                return true;
        }

        if (id == R.id .audio_view_add_playlist) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
            Bundle args = new Bundle();
            args.putParcelableArrayList(SavePlaylistDialog.KEY_NEW_TRACKS, adapter.getMedias(position));
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.setCallBack(updatePlaylists);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }

        int startPosition;
        ArrayList<MediaWrapper> medias;

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;

        // Play/Append
        if (useAllItems) {
            if (mSongsAdapter.getCount() <= position)
                return false;
            medias = new ArrayList<MediaWrapper>();
            startPosition = mSongsAdapter.getListWithPosition(medias, position);
        } else {
            startPosition = 0;
            if (mode == MODE_PLAYLIST){ //For file playlist, we browse tracks with mediabrowser, and add them in callbacks onMediaAdded and onBrowseEnd
                    medias = mPlaylistAdapter.getMedias(position);
                    if (medias.size() == 1 && mPlaylistAdapter.getMedias(position).get(0).getType() == MediaWrapper.TYPE_PLAYLIST) {
                        if (mMediaBrowser == null)
                            mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
                        mMediaBrowser.browse(mPlaylistAdapter.getMedias(position).get(0).getUri(), true);
                        return true;
                    }
            }
            if (position >= adapter.getCount())
                return false;
            medias = adapter.getMedias(position);
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
        List<MediaWrapper> medias = new ArrayList<MediaWrapper>();
        mSongsAdapter.getListWithPosition(medias, 0);
        if (mSongsAdapter.getCount() > 0) {
            Random rand = new Random();
            int randomSong = rand.nextInt(mSongsAdapter.getCount());
            if (mService != null) {
                mService.load(medias, randomSong);
                mService.shuffle();
            }
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
            MediaLibrary.getInstance().scanMediaItems(true);
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

    private void updateEmptyView(int position) {
        if (position == MODE_PLAYLIST){
            mEmptyView.setVisibility(mPlaylistAdapter.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.noplaylist);
        } else {
            mEmptyView.setVisibility(mAudioList == null || mAudioList.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.nomedia);
        }
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
//        mViewPager.setCurrentItem(position);
        updateEmptyView(position);
        setFabPlayShuffleAllVisibility();
//        tcl.onPageSelected(position);
    }

    private void deleteMedia(final MediaWrapper mw) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final String path = mw.getUri().getPath();
                FileUtils.deleteFile(path);
                MediaDatabase.getInstance().removeMedia(mw.getUri());
                mMediaLibrary.getMediaItems().remove(mw);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mService != null)
                            mService.removeLocation(mw.getLocation());
                    }
                });
                mHandler.obtainMessage(REFRESH, path).sendToTarget();
            }
        });
    }

    private void deletePlaylist(final AudioBrowserListAdapter.ListItem listItem) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                if (!MediaDatabase.getInstance().playlistExists(listItem.mTitle)) { //File playlist
                    MediaWrapper media = listItem.mMediaList.get(0);
                    mMediaLibrary.getMediaItems().remove(media);
                    FileUtils.deleteFile(media.getUri().getPath());
                    mHandler.obtainMessage(REFRESH, media.getLocation()).sendToTarget();
                } else {
                    MediaDatabase.getInstance().playlistDelete(listItem.mTitle);
                }
                mHandler.obtainMessage(UPDATE_LIST).sendToTarget();
            }
        });
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        tcl.onPageScrollStateChanged(state);
    }

    private static class AudioBrowserHandler extends WeakHandler<AudioBrowserFragment> {
        public AudioBrowserHandler(AudioBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            final AudioBrowserFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                    fragment.updateLists();
                    break;
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

    private void updateLists() {
        mAudioList = MediaLibrary.getInstance().getAudioItems();
        if (mAudioList.isEmpty()){
            updateEmptyView(mViewPager.getCurrentItem());
            mSwipeRefreshLayout.setRefreshing(false);
            mTabLayout.setVisibility(View.GONE);
        } else {
            mTabLayout.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);

            final ArrayList<Runnable> tasks = new ArrayList<Runnable>(Arrays.asList(updateArtists,
                    updateAlbums, updateSongs, updateGenres, updatePlaylists));

            //process the visible list first
            if (mViewPager.getCurrentItem() != 0)
                tasks.add(0, tasks.remove(mViewPager.getCurrentItem()));
            tasks.add(new Runnable() {
                @Override
                public void run() {
                    if (!mAdaptersToNotify.isEmpty())
                        display();
                }
            });
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    for (Runnable task : tasks)
                        task.run();
                }
            });
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
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mPlaylistAdapter.clear();
                    }
                });
            }
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

    private void updatePlaylists() {
        VLCApplication.runBackground(updatePlaylists);
    }

    AudioBrowserListAdapter.ContextPopupMenuListener mContextPopupMenuListener
            = new AudioBrowserListAdapter.ContextPopupMenuListener() {

        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public void onPopupMenu(View anchor, final int position) {
            if (!AndroidUtil.isHoneycombOrLater()) {
                // Call the "classic" context menu
                anchor.performLongClick();
                return;
            }

            PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
            popupMenu.getMenuInflater().inflate(R.menu.audio_list_browser, popupMenu.getMenu());
            setContextMenuItems(popupMenu.getMenu(), position);

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
