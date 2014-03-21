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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.widget.FlingViewGroup;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;

public class AudioAlbumsSongsFragment extends SherlockFragment {

    public final static String TAG = "VLC/AudioAlbumsSongsFragment";

    AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private AudioBrowserListAdapter mSongsAdapter;
    private AudioBrowserListAdapter mAlbumsAdapter;

    public final static String EXTRA_NAME = "name";
    public final static String EXTRA_NAME2 = "name2";
    public final static String EXTRA_MODE = "mode";

    private ArrayList<Media> mediaList;
    private String mTitle;

    TabHost mTabHost;
    FlingViewGroup mFlingViewGroup;
    private int mCurrentTab = 0;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioAlbumsSongsFragment() { }

    public void setMediaList(ArrayList<Media> mediaList, String title) {
        this.mediaList = mediaList;
        mTitle = title;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAlbumsAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITH_COVER);
        mSongsAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITH_COVER);

        mAlbumsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mSongsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);

        mAudioController = AudioServiceController.getInstance();
        mMediaLibrary = MediaLibrary.getInstance(getActivity());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getSherlockActivity().getSupportActionBar().setTitle(mTitle);

        View v = inflater.inflate(R.layout.audio_albums_songs, container, false);

        mTabHost = (TabHost) v.findViewById(android.R.id.tabhost);
        ListView albumsList = (ListView) v.findViewById(R.id.albums);
        ListView songsList = (ListView) v.findViewById(R.id.songs);
        mFlingViewGroup = (FlingViewGroup) v.findViewById(R.id.fling_view_group);

        songsList.setAdapter(mSongsAdapter);
        albumsList.setAdapter(mAlbumsAdapter);

        songsList.setOnItemClickListener(songsListener);
        albumsList.setOnItemClickListener(albumsListener);

        registerForContextMenu(albumsList);
        registerForContextMenu(songsList);

        mTabHost.setup();

        addNewTab(mTabHost, "albums", "Albums");
        addNewTab(mTabHost, "songs", "Songs");

        mTabHost.setCurrentTab(mCurrentTab);
        mFlingViewGroup.snapToScreen(mCurrentTab);

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                mCurrentTab = mTabHost.getCurrentTab();
                mFlingViewGroup.smoothScrollTo(mCurrentTab);
            }
        });

        mFlingViewGroup.setOnViewSwitchedListener(new FlingViewGroup.ViewSwitchListener() {
            @Override
            public void onSwitching(float progress) { }
            @Override
            public void onSwitched(int position) {
                mTabHost.setCurrentTab(position);
            }
            @Override
            public void onTouchDown() {}
            @Override
            public void onTouchUp() {}
            @Override
            public void onTouchClick() {}
        });

        return v;
    }

    private class DummyContentFactory implements TabHost.TabContentFactory {
        private final Context mContext;
        public DummyContentFactory(Context ctx) {
            mContext = ctx;
        }
        @Override
        public View createTabContent(String tag) {
            View dummy = new View(mContext);
            return dummy;
        }
    }

    private void addNewTab(TabHost tabHost, String tag, String title) {
        DummyContentFactory dcf = new DummyContentFactory(tabHost.getContext());
        TabSpec tabSpec = tabHost.newTabSpec(tag);
        tabSpec.setIndicator(getNewTabIndicator(tabHost.getContext(), title));
        tabSpec.setContent(dcf);
        tabHost.addTab(tabSpec);
    }

    private View getNewTabIndicator(Context context, String title) {
        View v = LayoutInflater.from(context).inflate(R.layout.tab_layout, null);
        TextView tv = (TextView) v.findViewById(R.id.textView);
        tv.setText(title);
        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        mCurrentTab = mTabHost.getCurrentTab();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);
        setContextMenuItems(menu, v);
    }

    private void setContextMenuItems(Menu menu, View v) {
        if (v.getId() != R.id.songs) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (!Util.isPhone())
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
                    new VlcRunnable(mSongsAdapter.getItem(groupPosition)) {
                        @Override
                        public void run(Object o) {
                            AudioBrowserListAdapter.ListItem listItem = (AudioBrowserListAdapter.ListItem)o;
                            Media media = listItem.mMediaList.get(0);
                            mMediaLibrary.getMediaItems().remove(media);
                            mSongsAdapter.removeMedia(media);
                            mAlbumsAdapter.removeMedia(media);
                            mAudioController.removeLocation(media.getLocation());
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
            switch (mTabHost.getCurrentTab())
            {
                case 0: // albums
                    medias = mAlbumsAdapter.getLocations(groupPosition);
                    break;
                case 1: // songs
                    medias = mSongsAdapter.getLocations(groupPosition);
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

    private void updateList() {
        if (mediaList == null)
            return;

        mAlbumsAdapter.clear();
        mSongsAdapter.clear();

        Collections.sort(mediaList, MediaComparators.byAlbum);
        String lastAlbumName = new String();

        for (int i = 0; i < mediaList.size(); ++i) {
            Media media = mediaList.get(i);
            mAlbumsAdapter.add(media.getAlbum(), null, media);

            if (!lastAlbumName.equals(media.getAlbum())) {
                mSongsAdapter.addSeparator(media.getAlbum());
                lastAlbumName = media.getAlbum();
            }
            mSongsAdapter.add(media.getTitle(), null, media);
        }
    }

    OnItemClickListener albumsListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> mediaLocation = mAlbumsAdapter.getLocations(p);
            mAudioController.load(mediaLocation, 0);
        }
    };

    OnItemClickListener songsListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> mediaLocation = mSongsAdapter.getLocations(p);
            mAudioController.load(mediaLocation, 0);
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
