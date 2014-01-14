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

import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.widget.FlingViewGroup;
import org.videolan.vlc.widget.FlingViewGroup.ViewSwitchListener;
import org.videolan.vlc.widget.HeaderScrollView;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
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
        updateLists();
        mMediaLibrary.addUpdateHandler(mHandler);
    }

    OnItemClickListener songListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<String> songList = new ArrayList<String>();
            int selectedId = mSongsAdapter.getListWithPosition(songList, p);
            mAudioController.load(songList, selectedId);
        }
    };

    OnItemClickListener artistListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            ArrayList<Media> mediaList = mArtistsAdapter.getMedia(p);
            AudioAlbumsSongsFragment frag = new AudioAlbumsSongsFragment(mediaList, mediaList.get(0).getArtist());
            MainActivity.ShowFragment(getActivity(), "albumsSongsFromArtist", frag);
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
            AudioAlbumsSongsFragment frag = new AudioAlbumsSongsFragment(mediaList, mediaList.get(0).getGenre());
            MainActivity.ShowFragment(getActivity(), "albumsSongsFromArtist", frag);
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);

        if (v.getId() != R.id.songs_list) {
            menu.setGroupEnabled(R.id.songs_view_only, false);
            menu.setGroupEnabled(R.id.phone_only, false);
        }
        if (!Util.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(!getUserVisibleHint()) return super.onContextItemSelected(item);

        ContextMenuInfo menuInfo = item.getMenuInfo();
        if(menuInfo == null) return super.onContextItemSelected(item);

        int startPosition;
        int groupPosition;
        int childPosition;
        List<String> medias;
        int id = item.getItemId();

        boolean useAllItems = (id == R.id.audio_list_browser_play_all ||
                               id == R.id.audio_list_browser_append_all);
        boolean append = (id == R.id.audio_list_browser_append ||
                          id == R.id.audio_list_browser_append_all);

        if (ExpandableListContextMenuInfo.class.isInstance(menuInfo)) {
            ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
            groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
            childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);
            if (childPosition < 0)
                childPosition = 0;
        }
        else {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            groupPosition = info.position;
            childPosition = 0;
        }

        if (id == R.id.audio_list_browser_delete) {
            /*AlertDialog alertDialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    mSongsAdapter.getLocation(groupPosition).get(0),
                    new VlcRunnable(mSongsAdapter.getItem(groupPosition)) {
                        @Override
                        public void run(Object o) {
                            Media aMedia = (Media) o;
                            mMediaLibrary.getMediaItems().remove(aMedia);
                            updateLists();
                        }
                    });
            alertDialog.show();*/
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            //AudioUtil.setRingtone(mSongsAdapter.getItem(groupPosition),getActivity());
            return true;
        }

        if (useAllItems) {
            startPosition = groupPosition;
            //medias = mSongsAdapter.getMedia(groupPosition);
            medias = new ArrayList<String>();
        }
        else {
            startPosition = 0;
            switch (mFlingViewGroup.getPosition())
            {
                case MODE_SONG:
                    //medias = mSongsAdapter.getMedia(groupPosition);
                    medias = new ArrayList<String>();
                    break;
                case MODE_ARTIST:
                    //medias = mArtistsAdapter.getMedia(groupPosition);
                    medias = new ArrayList<String>();
                    break;
                case MODE_ALBUM:
                    //medias = mArtistsAdapter.getMedia(groupPosition);
                    medias = new ArrayList<String>();
                    break;
                case MODE_GENRE:
                    //medias = mGenresAdapter.getMedia(groupPosition);
                    medias = new ArrayList<String>();
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
}
