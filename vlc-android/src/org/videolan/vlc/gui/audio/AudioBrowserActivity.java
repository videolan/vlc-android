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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.widget.FlingViewGroup;
import org.videolan.vlc.widget.FlingViewGroup.ViewSwitchListener;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class AudioBrowserActivity extends Activity implements ISortable {
    public final static String TAG = "VLC/AudioBrowserActivity";

    private FlingViewGroup mFlingViewGroup;

    private HorizontalScrollView mHeader;
    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private AudioSongsListAdapter mSongsAdapter;
    private AudioPlaylistAdapter mArtistsAdapter;
    private AudioPlaylistAdapter mAlbumsAdapter;
    private AudioPlaylistAdapter mGenresAdapter;

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private boolean mSortReverse = false;
    private int mSortBy = SORT_BY_TITLE;
    public final static int MODE_SONG = 0;
    public final static int MODE_ARTIST = 1;
    public final static int MODE_ALBUM = 2;
    public final static int MODE_GENRE = 3;
    public final static int MENU_PLAY = Menu.FIRST;
    public final static int MENU_APPEND = Menu.FIRST + 1;
    public final static int MENU_PLAY_ALL = Menu.FIRST + 2;
    public final static int MENU_APPEND_ALL = Menu.FIRST + 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_browser);

        mFlingViewGroup = (FlingViewGroup) findViewById(R.id.content);
        mFlingViewGroup.setOnViewSwitchedListener(mViewSwitchListener);

        mHeader = (HorizontalScrollView) findViewById(R.id.header);
        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(this);
        mMediaLibrary.addUpdateHandler(mHandler);

        mSongsAdapter = new AudioSongsListAdapter(this);
        mArtistsAdapter = new AudioPlaylistAdapter(this, R.plurals.albums, R.plurals.songs);
        mAlbumsAdapter = new AudioPlaylistAdapter(this, R.plurals.songs, R.plurals.songs);
        mGenresAdapter = new AudioPlaylistAdapter(this, R.plurals.albums, R.plurals.songs);
        ListView songsList = (ListView) findViewById(R.id.songs_list);
        ExpandableListView artistList = (ExpandableListView) findViewById(R.id.artists_list);
        ExpandableListView albumList = (ExpandableListView) findViewById(R.id.albums_list);
        ExpandableListView genreList = (ExpandableListView) findViewById(R.id.genres_list);
        songsList.setAdapter(mSongsAdapter);
        artistList.setAdapter(mArtistsAdapter);
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setAdapter(mGenresAdapter);
        songsList.setOnItemClickListener(songListener);
        artistList.setOnGroupClickListener(playlistListener);
        albumList.setOnGroupClickListener(playlistListener);
        genreList.setOnGroupClickListener(playlistListener);
        artistList.setOnChildClickListener(playlistChildListener);
        albumList.setOnChildClickListener(playlistChildListener);
        genreList.setOnChildClickListener(playlistChildListener);
        songsList.setOnCreateContextMenuListener(contextMenuListener);
        artistList.setOnCreateContextMenuListener(contextMenuListener);
        albumList.setOnCreateContextMenuListener(contextMenuListener);
        genreList.setOnCreateContextMenuListener(contextMenuListener);

        updateLists();
    }

    OnItemClickListener songListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            mAudioController.load(mSongsAdapter.getLocations(), p);
            Intent intent = new Intent(AudioBrowserActivity.this, AudioPlayerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    };

    OnGroupClickListener playlistListener = new OnGroupClickListener() {
        @Override
        public boolean onGroupClick(ExpandableListView elv, View v, int groupPosition, long id) {
            AudioPlaylistAdapter adapter = (AudioPlaylistAdapter) elv.getExpandableListAdapter();
            if (adapter.getChildrenCount(groupPosition) > 2)
                return false;

            String name = adapter.getGroup(groupPosition);
            Intent intent = new Intent(AudioBrowserActivity.this, AudioListActivity.class);
            AudioListActivity.set(intent, name, null, mFlingViewGroup.getPosition());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        }
    };

    OnChildClickListener playlistChildListener = new OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView elv, View v, int groupPosition, int childPosition, long id) {
            AudioPlaylistAdapter adapter = (AudioPlaylistAdapter) elv.getExpandableListAdapter();
            String name = adapter.getGroup(groupPosition);
            String child = adapter.getChild(groupPosition, childPosition);
            Intent intent = new Intent(AudioBrowserActivity.this, AudioListActivity.class);
            AudioListActivity.set(intent, name, child, mFlingViewGroup.getPosition());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return false;
        }
    };

    OnCreateContextMenuListener contextMenuListener = new OnCreateContextMenuListener()
    {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            menu.add(Menu.NONE, MENU_PLAY, Menu.NONE, R.string.play);
            menu.add(Menu.NONE, MENU_APPEND, Menu.NONE, R.string.append);
            if (v.getId() == R.id.songs_list) {
                menu.add(Menu.NONE, MENU_PLAY_ALL, Menu.NONE, R.string.play_all);
                menu.add(Menu.NONE, MENU_APPEND_ALL, Menu.NONE, R.string.append_all);
            }
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int startPosition;
        int groupPosition;
        int childPosition;
        List<String> medias;
        int id = item.getItemId();
        boolean play_all = id == MENU_PLAY_ALL || id == MENU_APPEND_ALL;
        boolean play_append = id == MENU_APPEND || id == MENU_APPEND_ALL;

        ContextMenuInfo menuInfo = item.getMenuInfo();
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

        if (play_all) {
            startPosition = groupPosition;
            medias = mSongsAdapter.getLocations();
        }
        else {
            startPosition = 0;
            switch (mFlingViewGroup.getPosition())
            {
                case MODE_SONG:
                    medias = mSongsAdapter.getLocation(groupPosition);
                    break;
                case MODE_ARTIST:
                    medias = mArtistsAdapter.getPlaylist(groupPosition, childPosition);
                    break;
                case MODE_ALBUM:
                    medias = mAlbumsAdapter.getPlaylist(groupPosition, childPosition);
                    break;
                case MODE_GENRE:
                    medias = mGenresAdapter.getPlaylist(groupPosition, childPosition);
                    break;
                default:
                    return true;
            }
        }
        if (play_append)
            mAudioController.append(medias);
        else
            mAudioController.load(medias, startPosition);

        Intent intent = new Intent(AudioBrowserActivity.this, AudioPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();
        super.onDestroy();
    }

    private ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {

        int mCurrentPosition = 0;

        @Override
        public void onSwitching(float progress) {
            LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
            int width = hl.getChildAt(0).getWidth();
            int x = (int) (progress * width);
            mHeader.smoothScrollTo(x, 0);
        }

        @Override
        public void onSwitched(int position) {
            LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
            TextView oldView = (TextView) hl.getChildAt(mCurrentPosition);
            oldView.setTextColor(Color.GRAY);
            TextView newView = (TextView) hl.getChildAt(position);
            newView.setTextColor(Color.WHITE);
            mCurrentPosition = position;
        }

    };

    /**
     * Handle changes on the list
     */
    protected Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                updateLists();
                break;
        }
    }
    };

    private Comparator<Media> byMRL = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            return String.CASE_INSENSITIVE_ORDER.compare(m1.getLocation(), m2.getLocation());
        };
    };

    private Comparator<Media> byLength = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            if(m1.getLength() > m2.getLength()) return -1;
            if(m1.getLength() < m2.getLength()) return 1;
            else return 0;
        };
    };

    private Comparator<Media> byAlbum = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getAlbum(), m2.getAlbum());
            if (res == 0)
                res = byMRL.compare(m1, m2);
            return res;
        };
    };

    private Comparator<Media> byArtist = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getArtist(), m2.getArtist());
            if (res == 0)
                res = byAlbum.compare(m1, m2);
            return res;
        };
    };

    private Comparator<Media> byGenre = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getGenre(), m2.getGenre());
            if (res == 0)
                res = byArtist.compare(m1, m2);
            return res;
        };
    };

    private void updateLists() {
        List<Media> audioList = MediaLibrary.getInstance(this).getAudioItems();
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();

        switch(mSortBy) {
        case SORT_BY_LENGTH:
            Collections.sort(audioList, byLength);
            break;
        case SORT_BY_TITLE:
        default:
            Collections.sort(audioList, byMRL);
            break;
        }
        if(mSortReverse) {
            Collections.reverse(audioList);
        }
        for (int i = 0; i < audioList.size(); i++)
            mSongsAdapter.add(audioList.get(i));

        Collections.sort(audioList, byArtist);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mArtistsAdapter.add(media.getArtist(), null, media);
            mArtistsAdapter.add(media.getArtist(), media.getAlbum(), media);
        }

        Collections.sort(audioList, byAlbum);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mAlbumsAdapter.add(media.getAlbum(), null, media);
        }

        Collections.sort(audioList, byGenre);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mGenresAdapter.add(media.getGenre(), null, media);
            mGenresAdapter.add(media.getGenre(), media.getAlbum(), media);
        }

        mSongsAdapter.notifyDataSetChanged();
        mArtistsAdapter.notifyDataSetChanged();
        mAlbumsAdapter.notifyDataSetChanged();
        mGenresAdapter.notifyDataSetChanged();
    }

    public void sortBy(int sortby) {
        if(mSortBy == sortby) {
            mSortReverse = !mSortReverse;
        } else {
            mSortBy = sortby;
            mSortReverse = false;
        }
        updateLists();
    }
}
