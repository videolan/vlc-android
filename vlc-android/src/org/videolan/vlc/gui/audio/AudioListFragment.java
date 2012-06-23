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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(getActivity());
        mMediaLibrary.addUpdateHandler(mHandler);

        mSongsAdapter = new AudioListAdapter(getActivity());
        setListAdapter(mSongsAdapter);

        mHandler.sendEmptyMessageDelayed(MediaLibrary.MEDIA_ITEMS_UPDATED, 250);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.audio_list, container, false);
        mTitle = (TextView) v.findViewById(R.id.title);
        registerForContextMenu(getListView());
        return v;
    }

    @Override
    public void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mSongsAdapter.clear();
        super.onDestroy();
    }

    public static void set(Intent intent, String name, String name2, int mode) {
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_NAME2, name2);
        intent.putExtra(EXTRA_MODE, mode);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mAudioController.load(mSongsAdapter.getLocations(), position);
        Intent intent = new Intent(getActivity(), AudioPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        super.onListItemClick(l, v, position, id);
    }

    public void deleteMedia( final List<String> addressMedia, final Media aMedia ) {
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
        .setTitle(R.string.confirm_delete)
        .setMessage(R.string.validation)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                URI adressMediaUri = null;
                try {
                    adressMediaUri = new URI (addressMedia.get(0));
                } catch (URISyntaxException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                File fileMedia =  new File(adressMediaUri);
                fileMedia.delete();
                mMediaLibrary.getMediaItems().remove(aMedia);
                updateList();
            }
        })
        .setNegativeButton(android.R.string.cancel, null).create();
        
        alertDialog.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        int startPosition;
        List<String> medias;
        int id = item.getItemId();

        boolean useAllItems = (id == R.id.audio_list_browser_play_all ||
                               id == R.id.audio_list_browser_append_all);
        boolean append = (id == R.id.audio_list_browser_append ||
                          id == R.id.audio_list_browser_append_all);

        if (id == R.id.audio_list_browser_delete) {
            deleteMedia(mSongsAdapter.getLocation(menuInfo.position),
                        mSongsAdapter.getItem(menuInfo.position));
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
        else
            mAudioController.load(medias, startPosition);

        Intent intent = new Intent(getActivity(), AudioPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        return super.onContextItemSelected(item);
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

    private Comparator<Media> byMRL = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
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
        final Bundle b = getArguments();
        String name = b.getString(EXTRA_NAME);
        String name2 = b.getString(EXTRA_NAME2);
        int mode = b.getInt(EXTRA_MODE, 0);

        List<Media> audioList;
        List<String> itemList;
        String currentItem = null;
        int currentIndex = -1;

        if (name == null || mode == AudioBrowserFragment.MODE_SONG) {
            mTitle.setText(R.string.songs);
            itemList = AudioServiceController.getInstance().getItems();
            currentItem = AudioServiceController.getInstance().getItem();
            audioList = MediaLibrary.getInstance(getActivity()).getMediaItems(itemList);
        }
        else {
            mTitle.setText(name2 != null ? name2 : name);
            audioList = MediaLibrary.getInstance(getActivity()).getAudioItems(name, name2, mode);
        }

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
        try {
            getListView().setSelection(currentIndex);
        } catch(IllegalStateException e) {
            /* Happens if updateList() message is received before onCreateView()
             * finishes. Nothing we can do here...
             */
        }

        mSongsAdapter.notifyDataSetChanged();
    }
}
