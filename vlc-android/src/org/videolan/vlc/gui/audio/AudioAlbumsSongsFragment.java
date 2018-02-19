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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentActivity;
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
import android.widget.Filter;

import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
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
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.List;

public class AudioAlbumsSongsFragment extends BaseAudioBrowser implements SwipeRefreshLayout.OnRefreshListener, TabLayout.OnTabSelectedListener {

    private final static String TAG = "VLC/AudioAlbumsSongsFragment";

    protected Handler mHandler = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ViewPager mViewPager;
    TabLayout mTabLayout;
    private ContextMenuRecyclerView[] mLists;
    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private FastScroller mFastScroller;
    //private View mSearchButtonView;

    private final static int MODE_ALBUM = 0;
    private final static int MODE_SONG = 1;
    private final static int MODE_TOTAL = 2; // Number of audio browser modes

    private MediaLibraryItem mItem;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioAlbumsSongsFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null)
            AudioBrowserAdapter.sMediaComparator.setSortDefault();

        mItem = (MediaLibraryItem) (savedInstanceState != null ?
                            savedInstanceState.getParcelable(AudioBrowserFragment.TAG_ITEM) :
                            getArguments().getParcelable(AudioBrowserFragment.TAG_ITEM));
    }

    @Override
    protected void display() {}

    @Override
    public String getTitle() {
        return mItem.getTitle();
    }

    public Filter getFilter() {
        return getCurrentAdapter().getFilter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_albums_songs, container, false);

        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        ContextMenuRecyclerView albumsList = (ContextMenuRecyclerView) mViewPager.getChildAt(MODE_ALBUM);
        ContextMenuRecyclerView songsList = (ContextMenuRecyclerView) mViewPager.getChildAt(MODE_SONG);

        mLists = new ContextMenuRecyclerView[]{albumsList, songsList};
        String[] titles = new String[] {getString(R.string.albums), getString(R.string.songs)};
        mAlbumsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_ALBUM, this, true);
        mSongsAdapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, true);
        mAlbumsAdapter.setParentAdapterType(mItem.getItemType());
        mSongsAdapter.setParentAdapterType(mItem.getItemType());
        mAdapters = new AudioBrowserAdapter[]{mAlbumsAdapter, mSongsAdapter};

        songsList.setAdapter(mSongsAdapter);
        albumsList.setAdapter(mAlbumsAdapter);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mFastScroller = (FastScroller) v.findViewById(R.id.songs_fast_scroller);

        mViewPager.setOnTouchListener(mSwipeFilter);
        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSearchButtonView = v.findViewById(R.id.searchButton);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView.RecycledViewPool rvp = new RecyclerView.RecycledViewPool();
        for (ContextMenuRecyclerView rv : mLists) {
            rv.setLayoutManager(new LinearLayoutManager(view.getContext()));
            LinearLayoutManager llm = new LinearLayoutManager(getActivity());
            llm.setRecycleChildrenOnDetach(true);
            rv.setLayoutManager(llm);
            rv.setRecycledViewPool(rvp);
        }
        mFabPlay.setImageResource(R.drawable.ic_fab_play);
        mTabLayout.addOnTabSelectedListener(this);
        updateList();
    }

    @Override
    public void onStart() {
        super.onStart();
        for (View rv : mLists)
            registerForContextMenu(rv);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (SecondaryActivity) context;
    }

    @Override
    public void onStop() {
        super.onStop();
        for (View rv : mLists)
            unregisterForContextMenu(rv);
    }

    @Override
    public void onRefresh() {
        mActivity.closeSearchView();
        updateList();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(AudioBrowserFragment.TAG_ITEM, mItem);
        super.onSaveInstanceState(outState);
    }

    protected void setContextMenuItems(Menu menu, int position) {
        if (mViewPager.getCurrentItem() != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (!AndroidDevices.isPhone)
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
        boolean insert_next = id == R.id.audio_list_browser_insert_next;

        if (id == R.id.audio_list_browser_delete) {

            adapter.remove(mediaItem);

            final Runnable cancel = new Runnable() {
                @Override
                public void run() {
                    adapter.addItems(mediaItem);
                }
            };
            UiTools.snackerWithCancel(mViewPager, getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(mediaItem, true, cancel);
                }
            }, cancel);
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
            args.putParcelableArray(SavePlaylistDialog.KEY_NEW_TRACKS, mediaItem.getTracks());
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }

        if (useAllItems) {
            List<MediaLibraryItem> items = new ArrayList<>();
            startPosition = mSongsAdapter.getListWithPosition(items, position);
            medias = items.toArray(new MediaWrapper[items.size()]);
        } else {
            startPosition = 0;
            if (mediaItem instanceof Album)
                medias = mediaItem.getTracks();
            else
                medias = new MediaWrapper[] {(MediaWrapper) mediaItem};
        }

        if (mService != null) {
            if (append)
                mService.append(medias);
            else if (insert_next)
                mService.insertNext(medias);
            else
                mService.load(medias, startPosition);
            return true;
        }
        return false;
    }

    private void updateList() {
        if (mItem == null || getActivity() == null) return;

        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final List<MediaLibraryItem> albums;
                if (mItem.getItemType() == MediaLibraryItem.TYPE_ARTIST) {
                    albums = Util.arrayToMediaArrayList(((Artist) mItem).getAlbums());
                } else if (mItem.getItemType() == MediaLibraryItem.TYPE_GENRE)
                    albums = Util.arrayToMediaArrayList(((Genre) mItem).getAlbums());
                else return;
                final List<MediaLibraryItem> songs = Util.arrayToMediaArrayList(mItem.getTracks());
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mAlbumsAdapter.update(albums);
                        mSongsAdapter.update(songs);
                    }
                });
            }
        });
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
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
            Intent i = new Intent(getActivity(), PlaylistActivity.class);
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item);
            startActivity(i);
        } else
            MediaUtils.openMedia(v.getContext(), (MediaWrapper) item);
    }

    @Override
    public void onCtxClick(View anchor, final int position, final MediaLibraryItem mediaItem) {
        if (mActionMode == null)
            mLists[mViewPager.getCurrentItem()].openContextMenu(position);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.supportInvalidateOptionsMenu();
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

    private ContextMenuRecyclerView getCurrentRV() {
        return mLists[mViewPager.getCurrentItem()];
    }

    protected boolean songModeSelected() {
        return mViewPager.getCurrentItem() == MODE_SONG;
    }

    @Override
    public void setFabPlayVisibility(boolean enable) {
        super.setFabPlayVisibility(enable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onFabPlayClick(View view) {
        if (mService == null) return;
        final List<MediaWrapper> list ;
        if (mViewPager.getCurrentItem() == 0) {
            list = new ArrayList<>();
            for (MediaLibraryItem item : mAlbumsAdapter.getMediaItems())
                list.addAll(Util.arrayToArrayList(item.getTracks()));
        } else {
            list = (List<MediaWrapper>) (List<?>) mSongsAdapter.getMediaItems();
        }
        mService.load(list, 0);
    }
}
