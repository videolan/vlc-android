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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.CommonDialogs;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

public class AudioListFragment extends SherlockListFragment {

    public final static String TAG = "VLC/AudioListFragment";

    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private TextView mTitle;
    private AudioListAdapter mSongsAdapter;

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private boolean mSortReverse = false;
    private int mSortBy = SORT_BY_TITLE;
    public final static String EXTRA_NAME = "name";
    public final static String EXTRA_NAME2 = "name2";
    public final static String EXTRA_MODE = "mode";

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioListFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(getActivity());

        mSongsAdapter = new AudioListAdapter(getActivity());
        setListAdapter(mSongsAdapter);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(getListView());
        updateList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.audio_list, container, false);
        mTitle = (TextView) v.findViewById(R.id.title);
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
        mMediaLibrary.addUpdateHandler(mHandler);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSongsAdapter.clear();
    }

    public static void set(Intent intent, String name, String name2, int mode) {
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_NAME2, name2);
        intent.putExtra(EXTRA_MODE, mode);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mAudioController.load(mSongsAdapter.getLocations(), position);
        super.onListItemClick(l, v, position, id);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);
        if (!Util.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(!getUserVisibleHint()) return super.onContextItemSelected(item);

        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        if(menuInfo == null) // getMenuInfo can be NULL
            return super.onContextItemSelected(item);

        int startPosition;
        List<String> medias;
        int id = item.getItemId();

        boolean useAllItems = (id == R.id.audio_list_browser_play_all ||
                               id == R.id.audio_list_browser_append_all);
        boolean append = (id == R.id.audio_list_browser_append ||
                          id == R.id.audio_list_browser_append_all);

        if (id == R.id.audio_list_browser_delete) {
            final Media media = mSongsAdapter.getItem(menuInfo.position);
            AlertDialog dialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    media.getLocation(),
                    new VlcRunnable(media) {
                        @Override
                        public void run(Object o) {
                            mMediaLibrary.getMediaItems().remove(media);
                            updateList();
                        }
                    });
            dialog.show();
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            AudioUtil.setRingtone(mSongsAdapter.getItem(menuInfo.position),getActivity());
            return true;
        }

        if (useAllItems) {
            startPosition = menuInfo.position;
            medias = mSongsAdapter.getLocations();
        }
        else {
            startPosition = 0;
            medias = mSongsAdapter.getLocation(menuInfo.position);
        }
        if (append)
            mAudioController.append(medias);
        else {
            mAudioController.load(medias, startPosition);
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioListHandler(this);

    private static class AudioListHandler extends WeakHandler<AudioListFragment> {
        public AudioListHandler(AudioListFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioListFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                    fragment.updateList();
                    break;
            }
        }
    };

    private Comparator<Media> byMRL = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            if( m1 == null)
                return -1;
            else if( m2 == null)
                return 1;
            return String.CASE_INSENSITIVE_ORDER.compare(m1.getLocation(), m2.getLocation());
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
        List<Media> audioList;
        List<String> itemList;
        String currentItem = null;
        int currentIndex = -1;

        mTitle.setText(R.string.songs);
        itemList = mAudioController.getMediaLocations();
        currentItem = mAudioController.getCurrentMediaLocation();
        audioList = MediaLibrary.getInstance(getActivity()).getMediaItems(itemList);

        mSongsAdapter.clear();
        switch (mSortBy) {
            case SORT_BY_LENGTH:
                Collections.sort(audioList, byLength);
                break;
            case SORT_BY_TITLE:
            default:
                Collections.sort(audioList, byMRL);
                break;
        }
        if (mSortReverse) {
            Collections.reverse(audioList);
        }

        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            if (currentItem != null && currentItem.equals(media.getLocation()))
                currentIndex = i;
            mSongsAdapter.add(media);
        }
        mSongsAdapter.setCurrentIndex(currentIndex);
        getListView().setSelection(currentIndex);

        mSongsAdapter.notifyDataSetChanged();
    }
}
