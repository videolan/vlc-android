/*****************************************************************************
 * AudioBrowserActivity.java
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
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.BrowserFragment;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCRunnable;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.FlingViewGroup;
import org.videolan.vlc.widget.FlingViewGroup.ViewSwitchListener;
import org.videolan.vlc.widget.HeaderScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioBrowserFragment extends BrowserFragment implements SwipeRefreshLayout.OnRefreshListener {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private FlingViewGroup mFlingViewGroup;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private int mFlingViewPosition = 0;

    private HeaderScrollView mHeader;
    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    List<MediaWrapper> mAudioList;
    private AudioBrowserListAdapter mArtistsAdapter;
    private AudioBrowserListAdapter mAlbumsAdapter;
    private AudioBrowserListAdapter mSongsAdapter;
    private AudioBrowserListAdapter mGenresAdapter;
    private ArrayList<AudioBrowserListAdapter> mAdaptersToNotify = new ArrayList<AudioBrowserListAdapter>();

    private View mEmptyView;

    public final static int MODE_TOTAL = 4; // Number of audio browser modes
    public final static int MODE_ARTIST = 0;
    public final static int MODE_ALBUM = 1;
    public final static int MODE_SONG = 2;
    public final static int MODE_GENRE = 3;

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

        mSongsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mArtistsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mAlbumsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mGenresAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(R.string.audio);

        View v = inflater.inflate(R.layout.audio_browser, container, false);

        mFlingViewGroup = (FlingViewGroup)v.findViewById(R.id.content);
        mFlingViewGroup.setOnViewSwitchedListener(mViewSwitchListener);

        mHeader = (HeaderScrollView)v.findViewById(R.id.header);
        mHeader.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                // prevent the user from scrolling the header
                return true;
            }
        });
        mHeader.setOnKeyListener(keyListener);

        mEmptyView = v.findViewById(R.id.no_media);

        ListView songsList = (ListView)v.findViewById(R.id.songs_list);
        ListView artistList = (ListView)v.findViewById(R.id.artists_list);
        ListView albumList = (ListView)v.findViewById(R.id.albums_list);
        ListView genreList = (ListView)v.findViewById(R.id.genres_list);

        songsList.setAdapter(mSongsAdapter);
        artistList.setAdapter(mArtistsAdapter);
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setAdapter(mGenresAdapter);

        songsList.setOnItemClickListener(songListener);
        artistList.setOnItemClickListener(artistListListener);
        albumList.setOnItemClickListener(albumListListener);
        genreList.setOnItemClickListener(genreListListener);

        artistList.setOnKeyListener(keyListener);
        albumList.setOnKeyListener(keyListener);
        songsList.setOnKeyListener(keyListener);
        genreList.setOnKeyListener(keyListener);

        registerForContextMenu(songsList);
        registerForContextMenu(artistList);
        registerForContextMenu(albumList);
        registerForContextMenu(genreList);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkerorange);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        songsList.setOnScrollListener(mScrollListener);
        artistList.setOnScrollListener(mScrollListener);
        albumList.setOnScrollListener(mScrollListener);
        genreList.setOnScrollListener(mScrollListener);

        return v;
    }

    AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener(){
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
                mSwipeRefreshLayout.setEnabled(firstVisibleItem == 0);
            }
    };

    @Override
    public void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);
    }

    @Override
    public void onResume() {
        super.onResume();
        mFlingViewGroup.setPosition(mFlingViewPosition);
        mHeader.highlightTab(-1, mFlingViewPosition);
        mHeader.scroll(mFlingViewPosition / 3.f);
        if (mMediaLibrary.isWorking())
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);
        else if (mGenresAdapter.isEmpty() || mArtistsAdapter.isEmpty() ||
                mAlbumsAdapter.isEmpty() || mSongsAdapter.isEmpty())
            updateLists();
        mMediaLibrary.addUpdateHandler(mHandler);
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
             *
             * ACTION_DOWN occurs before focus change and
             * may be used to find if change originated from the
             * header or if the header must be updated explicitely with
             * a call to mHeader.scroll(...).
             */
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int newPosition = mFlingViewPosition;

                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (newPosition < (MODE_TOTAL - 1))
                            newPosition++;
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (newPosition > 0)
                            newPosition--;
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        mFlingViewPosition = 0xFF;
                        break;
                    default:
                        return false;
                }

                if (newPosition != mFlingViewPosition) {
                    int[] lists = { R.id.artists_list, R.id.albums_list,
                        R.id.songs_list, R.id.genres_list };
                    ListView vList = (ListView)v.getRootView().
                        findViewById(lists[newPosition]);

                    if (!mHeader.isFocused())
                        mHeader.scroll(newPosition / 3.f);

                    if (vList.getCount() == 0)
                        mHeader.setNextFocusDownId(R.id.header);
                    else
                        mHeader.setNextFocusDownId(lists[newPosition]);

                    mFlingViewGroup.scrollTo(newPosition);

                    // assigned in onSwitched following mHeader.scroll
                    mFlingViewPosition = newPosition;

                    ((MainActivity)getActivity()).setSearchAsFocusDown(
                        vList.getCount() == 0, getView(),
                        lists[newPosition]);
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
            AudioAlbumsSongsFragment frag = (AudioAlbumsSongsFragment)activity.showSecondaryFragment("albumsSongs");
            if (frag != null) {
                frag.setMediaList(mediaList, Util.getMediaArtist(activity, mediaList.get(0)));
            }
        }
    };

    OnItemClickListener albumListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> mediaLocation = mAlbumsAdapter.getLocations(p, true);
            mAudioController.load(mediaLocation, 0);
        }
    };

    OnItemClickListener genreListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<MediaWrapper> mediaList = mGenresAdapter.getMedia(p);
            MainActivity activity = (MainActivity)getActivity();
            AudioAlbumsSongsFragment frag = (AudioAlbumsSongsFragment)activity.showSecondaryFragment("albumsSongs");
            if (frag != null) {
                frag.setMediaList(mediaList, Util.getMediaGenre(activity, mediaList.get(0)));
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
        final int pos = mFlingViewGroup.getPosition();
        if (pos != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (pos == MODE_ARTIST || pos == MODE_GENRE) {
            MenuItem play = menu.findItem(R.id.audio_list_browser_play);
            play.setVisible(true);
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

    private boolean handleContextItemSelected(MenuItem item, int position) {
        ContextMenuInfo menuInfo = item.getMenuInfo();

        int startPosition;
        int groupPosition;
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
            AlertDialog alertDialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    mSongsAdapter.getLocations(groupPosition).get(0),
                    new VLCRunnable(mSongsAdapter.getItem(groupPosition)) {
                        @Override
                        public void run(Object o) {
                            AudioBrowserListAdapter.ListItem listItem = (AudioBrowserListAdapter.ListItem)o;
                            MediaWrapper media = listItem.mMediaList.get(0);
                            mMediaLibrary.getMediaItems().remove(media);
                            mAudioController.removeLocation(media.getLocation());
                            updateLists();
                        }
                    });
            alertDialog.show();
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            AudioUtil.setRingtone(mSongsAdapter.getItem(groupPosition).mMediaList.get(0), getActivity());
            return true;
        }

        if (useAllItems) {
            medias = new ArrayList<String>();
            startPosition = mSongsAdapter.getListWithPosition(medias, groupPosition);
        }
        else {
            startPosition = 0;
            switch (mFlingViewGroup.getPosition())
            {
                case MODE_SONG:
                    medias = mSongsAdapter.getLocations(groupPosition);
                    break;
                case MODE_ARTIST:
                    medias = mArtistsAdapter.getLocations(groupPosition);
                    break;
                case MODE_ALBUM:
                    medias = mAlbumsAdapter.getLocations(groupPosition, true);
                    break;
                case MODE_GENRE:
                    medias = mGenresAdapter.getLocations(groupPosition);
                    break;
                default:
                    return true;
            }
        }

        if (append)
            mAudioController.append(medias);
        else
            mAudioController.load(medias, startPosition);

        return super.onContextItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();
    }

    private final ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {

        @Override
        public void onSwitching(float progress) {
            mHeader.scroll(progress);
        }

        @Override
        public void onSwitched(int position) {
            mHeader.highlightTab(mFlingViewPosition, position);
            mFlingViewPosition = position;
        }

        @Override
        public void onTouchDown() {}

        @Override
        public void onTouchUp() {}

        @Override
        public void onTouchClick() {}

        @Override
        public void onBackSwitched() {}
    };

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
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
                    synchronized (mAdaptersToNotify) {
                        for (AudioBrowserListAdapter adapter : mAdaptersToNotify)
                            adapter.notifyDataSetChanged();
                        mAdaptersToNotify.clear();
                    }

                    // Refresh the fast scroll data, since SectionIndexer doesn't respect notifyDataSetChanged
                    int[] lists = {R.id.artists_list, R.id.albums_list, R.id.songs_list, R.id.genres_list};
                    if (getView() != null) {
                        ListView l;
                        for (int r : lists) {
                            l = (ListView) getView().findViewById(r);
                            l.setFastScrollEnabled(true);
                        }
                    }
                    focusHelper(false, R.id.artists_list);
                    mHandler.removeMessages(MSG_LOADING);
                    mSwipeRefreshLayout.setRefreshing(false);
                    mDisplaying = false;
                }
            });
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
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();
        mAudioList = MediaLibrary.getInstance().getAudioItems();
        if (mAudioList.isEmpty()){
            mSwipeRefreshLayout.setRefreshing(false);
            mEmptyView.setVisibility(View.VISIBLE);
            focusHelper(true, R.id.artists_list);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);

            ExecutorService tpe = Executors.newSingleThreadExecutor();
            ArrayList<Runnable> tasks = new ArrayList<Runnable>();
            tasks.add(updateArtists);
            tasks.add(updateAlbums);
            tasks.add(updateSongs);
            tasks.add(updateGenres);
            //process the visible list first
            tasks.add(0, tasks.remove(mFlingViewPosition));
            tasks.add(new Runnable() {
                @Override
                public void run() {
                    synchronized (mAdaptersToNotify) {
                        if (!mAdaptersToNotify.isEmpty())
                            display();
                    }
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
            synchronized (mAdaptersToNotify) {
                mAdaptersToNotify.add(mArtistsAdapter);
            }
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateAlbums = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byAlbum);
            mAlbumsAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_ALBUMS);
            synchronized (mAdaptersToNotify) {
                mAdaptersToNotify.add(mAlbumsAdapter);
            }
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateSongs = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byName);
            mSongsAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_SONGS);
            synchronized (mAdaptersToNotify) {
                mAdaptersToNotify.add(mSongsAdapter);
            }
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateGenres = new Runnable() {
        @Override
        public void run() {
            Collections.sort(mAudioList, MediaComparators.byGenre);
            mGenresAdapter.addAll(mAudioList, AudioBrowserListAdapter.TYPE_GENRES);
            synchronized (mAdaptersToNotify) {
                mAdaptersToNotify.add(mGenresAdapter);
            }
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
}
