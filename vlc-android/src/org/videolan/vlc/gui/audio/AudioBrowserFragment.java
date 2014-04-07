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
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.widget.FlingViewGroup;
import org.videolan.vlc.widget.FlingViewGroup.ViewSwitchListener;
import org.videolan.vlc.widget.HeaderScrollView;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.actionbarsherlock.app.SherlockFragment;

public class AudioBrowserFragment extends SherlockFragment {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private FlingViewGroup mFlingViewGroup;
    private int mFlingViewPosition = 0;

    private HeaderScrollView mHeader;
    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private AudioBrowserListAdapter mSongsAdapter;
    private AudioBrowserListAdapter mArtistsAdapter;
    private AudioBrowserListAdapter mAlbumsAdapter;
    private AudioBrowserListAdapter mGenresAdapter;

    private View mEmptyView;

    public final static int MODE_TOTAL = 4; // Number of audio browser modes
    public final static int MODE_ARTIST = 0;
    public final static int MODE_ALBUM = 1;
    public final static int MODE_SONG = 2;
    public final static int MODE_GENRE = 3;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioBrowserFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(getActivity());

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
        getSherlockActivity().getSupportActionBar().setTitle(R.string.audio);

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

        registerForContextMenu(songsList);
        registerForContextMenu(artistList);
        registerForContextMenu(albumList);
        registerForContextMenu(genreList);

        return v;
    }

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
        updateLists();
        mMediaLibrary.addUpdateHandler(mHandler);
    }

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
            ArrayList<Media> mediaList = mArtistsAdapter.getMedia(p);
            MainActivity activity = (MainActivity)getActivity();
            AudioAlbumsSongsFragment frag = (AudioAlbumsSongsFragment)activity.showSecondaryFragment("albumsSongs");
            if (frag != null) {
                frag.setMediaList(mediaList, mediaList.get(0).getArtist());
            }
        }
    };

    OnItemClickListener albumListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> mediaLocation = mAlbumsAdapter.getLocations(p);
            mAudioController.load(mediaLocation, 0);
        }
    };

    OnItemClickListener genreListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<Media> mediaList = mGenresAdapter.getMedia(p);
            MainActivity activity = (MainActivity)getActivity();
            AudioAlbumsSongsFragment frag = (AudioAlbumsSongsFragment)activity.showSecondaryFragment("albumsSongs");
            if (frag != null) {
                frag.setMediaList(mediaList, mediaList.get(0).getGenre());
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
        if (pos == MODE_ARTIST || v.getId() == MODE_GENRE) {
            MenuItem play = menu.findItem(R.id.audio_list_browser_play);
            play.setVisible(true);
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
                    medias = mArtistsAdapter.getLocations(groupPosition);
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
    };

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioBrowserHandler(this);

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
            }
        }
    };

    private void updateLists() {
        List<Media> audioList = MediaLibrary.getInstance(getActivity()).getAudioItems();

        if (audioList.isEmpty())
            mEmptyView.setVisibility(View.VISIBLE);
        else
            mEmptyView.setVisibility(View.GONE);

        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();

        Collections.sort(audioList, MediaComparators.byName);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mSongsAdapter.add(media.getTitle(), media.getArtist(), media);
        }

        Collections.sort(audioList, MediaComparators.byArtist);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mArtistsAdapter.add(media.getArtist(), null, media);
        }
        mArtistsAdapter.addLeterSeparators();

        Collections.sort(audioList, MediaComparators.byAlbum);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mAlbumsAdapter.add(media.getAlbum(), media.getArtist(), media);
        }
        mAlbumsAdapter.addLeterSeparators();

        Collections.sort(audioList, MediaComparators.byGenre);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mGenresAdapter.add(media.getGenre(), null, media);
        }
        mGenresAdapter.addLeterSeparators();

        mSongsAdapter.notifyDataSetChanged();
        mArtistsAdapter.notifyDataSetChanged();
        mAlbumsAdapter.notifyDataSetChanged();
        mGenresAdapter.notifyDataSetChanged();
    }

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
