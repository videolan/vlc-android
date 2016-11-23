/*****************************************************************************
 * AudioListActivity.java
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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioAlbumsSongsFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, AudioBrowserAdapter.EventsHandler, TabLayout.OnTabSelectedListener {

    private final static String TAG = "VLC/AudioAlbumsSongsFragment";

    public final static String TAG_ITEM = "ML_ITEM";

    private Medialibrary mMediaLibrary;
    protected Handler mHandler = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ViewPager mViewPager;
    TabLayout mTabLayout;
    private List<View> mLists;
    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private FastScroller mFastScroller;

    private final static int MODE_ALBUM = 0;
    private final static int MODE_SONG = 1;
    private final static int MODE_TOTAL = 2; // Number of audio browser modes

    private MediaLibraryItem mItem;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioAlbumsSongsFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAlbumsAdapter = new AudioBrowserAdapter(getActivity(), this, false);
        mSongsAdapter = new AudioBrowserAdapter(getActivity(), this, false);

        mMediaLibrary = VLCApplication.getMLInstance();
        mItem = (MediaLibraryItem) (savedInstanceState != null ?
                            savedInstanceState.getParcelable(TAG_ITEM) :
                            getArguments().getParcelable(TAG_ITEM));
    }

    @Override
    protected void display() {}

    @Override
    protected String getTitle() {
        return mItem.getTitle();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_albums_songs, container, false);

        ContextMenuRecyclerView albumsList = (ContextMenuRecyclerView) v.findViewById(R.id.albums);
        ContextMenuRecyclerView songsList = (ContextMenuRecyclerView) v.findViewById(R.id.songs);
        albumsList.setLayoutManager(new LinearLayoutManager(container.getContext()));
        songsList.setLayoutManager(new LinearLayoutManager(container.getContext()));

        mLists = Arrays.asList((View)albumsList, songsList);
        String[] titles = new String[] {getString(R.string.albums), getString(R.string.songs)};
        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mFastScroller = (FastScroller) v.findViewById(R.id.songs_fast_scroller);

        mViewPager.setOnTouchListener(mSwipeFilter);
        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        songsList.setAdapter(mSongsAdapter);
        albumsList.setAdapter(mAlbumsAdapter);

        registerForContextMenu(albumsList);
        registerForContextMenu(songsList);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFastScroller.setRecyclerView((RecyclerView) mLists.get(mViewPager.getCurrentItem()));
        mTabLayout.addOnTabSelectedListener(this);
        updateList();
    }

    @Override
    public void onRefresh() {
        updateList();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(TAG_ITEM, mItem);
        super.onSaveInstanceState(outState);
    }

    protected void setContextMenuItems(Menu menu, int position) {
        if (mViewPager.getCurrentItem() != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (!AndroidDevices.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
        menu.findItem(R.id.audio_list_browser_play).setVisible(true);
        //Hide delete if we cannot
        final AudioBrowserAdapter adapter = mViewPager.getCurrentItem() == MODE_ALBUM ? mAlbumsAdapter : mSongsAdapter;
        final MediaLibraryItem mediaItem = adapter.getItem(position);
        String location = mediaItem instanceof MediaWrapper ? ((MediaWrapper)mediaItem).getLocation() : null;
        menu.findItem(R.id.audio_list_browser_delete).setVisible(location != null &&
                FileUtils.canWrite(location));
    }

    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        int startPosition;
        MediaWrapper[] medias;
        int id = item.getItemId();
        final AudioBrowserAdapter adapter = mViewPager.getCurrentItem() == MODE_ALBUM ? mAlbumsAdapter : mSongsAdapter;
        final MediaLibraryItem mediaItem = adapter.getItem(position);

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;

        if (id == R.id.audio_list_browser_delete) {

            adapter.remove(position);

            UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(mediaItem, true);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    adapter.addItem(position, mediaItem);
                }
            });
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            AudioUtil.setRingtone((MediaWrapper) mediaItem, getActivity());
            return true;
        }

        if (id == R.id.audio_view_info) {
            showInfoDialog((MediaWrapper) mediaItem);
            return true;
        }

        if (id == R.id .audio_view_add_playlist) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
            Bundle args = new Bundle();
            args.putParcelable(SavePlaylistDialog.KEY_NEW_TRACKS, mediaItem);
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }

        if (useAllItems) {
            ArrayList<MediaLibraryItem> items = new ArrayList<>();
            startPosition = mSongsAdapter.getListWithPosition(items, position);
            medias = (MediaWrapper[]) items.toArray();
        } else {
            startPosition = 0;
            if (mediaItem instanceof Album)
                medias = mediaItem.getTracks(mMediaLibrary);
            else
                medias = new MediaWrapper[] {(MediaWrapper) mediaItem};
        }

        if (mService != null) {
            if (append)
                mService.append(medias);
            else
                mService.load(medias, startPosition);
            return true;
        }
        return false;
    }

    private void updateList() {
        if (mItem == null || getActivity() == null)
            return;

        mAlbumsAdapter.clear();
        mSongsAdapter.clear();
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final Album[] albums;
                final MediaWrapper[] songs;
                if (mItem instanceof Artist) {
                    albums = ((Artist) mItem).getAlbums(mMediaLibrary);
                    songs = mItem.getTracks(mMediaLibrary);
                } else if (mItem instanceof Genre) {
                    albums = ((Genre) mItem).getAlbums(mMediaLibrary);
                    songs = mItem.getTracks(mMediaLibrary);
                } else
                    return;
                mSongsAdapter.addAll(songs);
                mAlbumsAdapter.addAll(albums);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        });
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

    public void clear() {
        mAlbumsAdapter.clear();
        mSongsAdapter.clear();
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (item instanceof Album) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUM);
            i.putExtra(AudioAlbumFragment.TAG_ITEM, item);
            startActivity(i);
        } else
            MediaUtils.openMedia(v.getContext(), (MediaWrapper) item);
    }

    @Override
    public void onCtxClick(View anchor, final int position, final MediaLibraryItem mediaItem) {
        ((ContextMenuRecyclerView)mLists.get(mViewPager.getCurrentItem())).openContextMenu(position);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mFastScroller.setRecyclerView((RecyclerView) mLists.get(tab.getPosition()));
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        stopActionMode();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        ((RecyclerView)mLists.get(tab.getPosition())).smoothScrollToPosition(0);
    }

    protected AudioBrowserAdapter getCurrentAdapter() {
        return (AudioBrowserAdapter) (getCurrentRV()).getAdapter();
    }

    private ContextMenuRecyclerView getCurrentRV() {
        return (ContextMenuRecyclerView)mLists.get(mViewPager.getCurrentItem());
    }

    protected boolean songModeSelected() {
        return mViewPager.getCurrentItem() == MODE_SONG;
    }
}
