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

package org.videolan.vlc.android;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

public class AudioListActivity extends ListActivity {

    public final static String TAG = "VLC/AudioBrowserActivity";

    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private TextView mTitle;
    private AudioSongsListAdapter mSongsAdapter;

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private boolean mSortReverse = false;
    private int mSortBy = SORT_BY_TITLE;
    public final static String EXTRA_NAME = "name";
    public final static String EXTRA_MODE = "mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_list);

        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(this);
        mMediaLibrary.addUpdateHandler(mHandler);

        mTitle = (TextView) findViewById(R.id.title);

        mSongsAdapter = new AudioSongsListAdapter(this, R.layout.audio_browser_item);
        setListAdapter(mSongsAdapter);
        getListView().setOnCreateContextMenuListener(contextMenuListener);

        updateList();
    }

    public static void set(Intent intent, String name, int mode) {
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_MODE, mode);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        mAudioController.load(mSongsAdapter.getPaths(), position);
        Intent intent = new Intent(AudioListActivity.this, AudioPlayerActivity.class);
        startActivity(intent);
        super.onListItemClick(l, v, position, id);
    }

    OnCreateContextMenuListener contextMenuListener = new OnCreateContextMenuListener()
    {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            menu.add(Menu.NONE, AudioBrowserActivity.MENU_PLAY, Menu.NONE, R.string.play);
            menu.add(Menu.NONE, AudioBrowserActivity.MENU_APPEND, Menu.NONE, R.string.append);
            menu.add(Menu.NONE, AudioBrowserActivity.MENU_PLAY_ALL, Menu.NONE, R.string.play_all);
            menu.add(Menu.NONE, AudioBrowserActivity.MENU_APPEND_ALL, Menu.NONE, R.string.append_all);
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        int id = item.getItemId();

        boolean play_all = id == AudioBrowserActivity.MENU_PLAY_ALL || id == AudioBrowserActivity.MENU_APPEND_ALL;
        boolean play_append = id == AudioBrowserActivity.MENU_APPEND || id == AudioBrowserActivity.MENU_APPEND_ALL;
        int start_position;
        List<String> medias;

        if (play_all) {
            start_position = menuInfo.position;
            medias = mSongsAdapter.getPaths();
        }
        else {
            start_position = 0;
            medias = mSongsAdapter.getPath(menuInfo.position);
        }
        if (play_append)
            mAudioController.append(medias);
        else
            mAudioController.load(medias, start_position);

        Intent intent = new Intent(AudioListActivity.this, AudioPlayerActivity.class);
        startActivity(intent);
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mSongsAdapter.clear();
        super.onDestroy();
    }

    /**
     * Handle changes on the list
     */
    protected Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                    updateList();
                    break;
            }
        }
    };

    private Comparator<Media> byPath = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            return String.CASE_INSENSITIVE_ORDER.compare(m1.getFile().getPath(), m2.getFile().getPath());
        };
    };

    private Comparator<Media> byLength = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            if (m1.getLength() > m2.getLength())
                return -1;
            if (m1.getLength() < m2.getLength())
                return 1;
            else
                return 0;
        };
    };

    private void updateList() {
        String name = getIntent().getStringExtra(EXTRA_NAME);
        int mode = getIntent().getIntExtra(EXTRA_MODE, 0);
        if (name == null || mode == 0)
            return;

        mTitle.setText(name);
        List<Media> audioList = MediaLibrary.getInstance(this).getAudioItems(name, mode);
        mSongsAdapter.clear();

        switch (mSortBy) {
            case SORT_BY_LENGTH:
                Collections.sort(audioList, byLength);
                break;
            case SORT_BY_TITLE:
            default:
                Collections.sort(audioList, byPath);
                break;
        }
        if (mSortReverse) {
            Collections.reverse(audioList);
        }
        for (int i = 0; i < audioList.size(); i++) {
            mSongsAdapter.add(audioList.get(i));
        }

        mSongsAdapter.notifyDataSetChanged();
    }
}
