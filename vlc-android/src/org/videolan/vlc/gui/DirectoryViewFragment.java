/*****************************************************************************
 * DirectoryViewFragment.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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
package org.videolan.vlc.gui;

import java.io.IOException;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.ISortable;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

public class DirectoryViewFragment extends SherlockListFragment implements ISortable {
    public final static String TAG = "VLC/DirectoryViewFragment";

    private DirectoryAdapter mDirectoryAdapter;

    /* All subclasses of Fragment must include a public empty constructor. */
    public DirectoryViewFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDirectoryAdapter = new DirectoryAdapter(getActivity());
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        getActivity().registerReceiver(messageReceiver, filter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getSherlockActivity().getSupportActionBar().setTitle(R.string.directories);

        View v = inflater.inflate(R.layout.directory_view, container, false);
        setListAdapter(mDirectoryAdapter);
        final ListView listView = (ListView)v.findViewById(android.R.id.list);
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View v, int position, long id) {
                if(mDirectoryAdapter.isChildFile(position)) {
                    return false;
                } else {
                    return true; /* Terminate the automatic context menu */
                }
            }
        });

        registerForContextMenu(listView);
        return v;
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(messageReceiver);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        int position = ((AdapterContextMenuInfo)menuInfo).position;

        if(mDirectoryAdapter.isChildFile(position)) {
            MenuInflater menuInflater = getActivity().getMenuInflater();
            menuInflater.inflate(R.menu.directory_view, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(!getUserVisibleHint()) return super.onContextItemSelected(item);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if(info == null) // info can be null
            return super.onContextItemSelected(item);

        int id = item.getItemId();
        String mediaLocation = mDirectoryAdapter.getMediaLocation(info.position);
        if (mediaLocation == null)
            return super.onContextItemSelected(item);

        if(id == R.id.directory_view_play) {
            openMediaFile(info.position);
            return true;
        } else if(id == R.id.directory_view_append) {
            AudioServiceController.getInstance().append(mediaLocation);
            return true;
        } else if(id == R.id.directory_view_delete) {
            AlertDialog alertDialog = CommonDialogs.deleteMedia(getActivity(), mediaLocation,
                    new VlcRunnable() {
                        @Override
                        public void run(Object o) {
                            refresh();
                        }
                    });
            alertDialog.show();
        } else if(id == R.id.directory_view_play_audio) {
            AudioServiceController.getInstance().load(mediaLocation, true);
        } else if(id == R.id.directory_view_play_video) {
            VideoPlayerActivity.start(getActivity(), mediaLocation);
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int p, long id) {
        int success = mDirectoryAdapter.browse(p);

        if(success < 0) /* Clicked on a media file */
            openMediaFile(p);
        else
            setSelection(success);
    }

    public boolean isRootDirectory () {
        return mDirectoryAdapter.isRoot();
    }

    public void showParentDirectory() {
        int success = mDirectoryAdapter.browse("..");

        if(success >= 0)
            setSelection(success);
    };

    private void openMediaFile(int p) {
        AudioServiceController audioController = AudioServiceController.getInstance();
        String mediaFile = mDirectoryAdapter.getMediaLocation(p);

        try {
            if (LibVLC.getExistingInstance() == null
                    || !LibVLC.getExistingInstance().hasVideoTrack(mediaFile)) {
                List<String> mediaLocations = mDirectoryAdapter.getAllMediaLocations();
                audioController.load(mediaLocations, mediaLocations.indexOf(mediaFile));
            } else {
                VideoPlayerActivity.start(getActivity(), mediaFile);
            }
        } catch (IOException e) {
            /* disk error maybe? */
        }
    }

    @Override
    public void sortBy(int sortby) {
        // TODO
        Util.toaster(getActivity(), R.string.notavailable);
    }

    public void refresh() {
        if (mDirectoryAdapter != null)
            mDirectoryAdapter.refresh();
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED) ||
                action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED) ||
                action.equalsIgnoreCase(Intent.ACTION_MEDIA_REMOVED) ||
                action.equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)) {
                refresh();
            }
        }
    };
}
