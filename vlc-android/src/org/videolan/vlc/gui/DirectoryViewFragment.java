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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCRunnable;
import org.videolan.vlc.widget.SwipeRefreshLayout;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

public class DirectoryViewFragment extends BrowserFragment implements IRefreshable, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {
    public final static String TAG = "VLC/DirectoryViewFragment";

    private DirectoryAdapter mDirectoryAdapter;
    private ListView mListView;

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
        focusHelper(mDirectoryAdapter.isEmpty());
    }

    private void focusHelper(boolean idIsEmpty) {
        View parent = View.inflate(getActivity(),
            R.layout.directory_view, null);
        MainActivity main = (MainActivity)getActivity();
        main.setMenuFocusDown(idIsEmpty, android.R.id.list);
        main.setSearchAsFocusDown(idIsEmpty, parent, android.R.id.list);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.directory_view, container, false);
        mListView = (ListView) v.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mDirectoryAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
        mListView.setAdapter(mDirectoryAdapter);
        mListView.setNextFocusUpId(R.id.ml_menu_search);
        mListView.setNextFocusLeftId(android.R.id.list);
        mListView.setNextFocusRightId(android.R.id.list);
        if (LibVlcUtil.isHoneycombOrLater())
            mListView.setNextFocusForwardId(android.R.id.list);
        focusHelper(mDirectoryAdapter.getCount() == 0);
        mListView.requestFocus();
        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mSwipeRefreshLayout.setEnabled(firstVisibleItem == 0);
            }
        });

        registerForContextMenu(mListView);
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
        MenuInflater menuInflater = getActivity().getMenuInflater();

        if(mDirectoryAdapter.isChildFile(position))
            menuInflater.inflate(R.menu.directory_view_file, menu);
        else {
            DirectoryAdapter.Node folder = (DirectoryAdapter.Node) mDirectoryAdapter.getItem(position);
            if (Util.canWrite(mDirectoryAdapter.getmCurrentDir()+"/"+folder.name)) {
                menuInflater.inflate(R.menu.directory_view_dir, menu);
                boolean nomedia = new File(mDirectoryAdapter.getmCurrentDir()+"/"+folder.name+"/.nomedia").exists();
                menu.findItem(R.id.directory_view_hide_media).setVisible(!nomedia);
                menu.findItem(R.id.directory_view_show_media).setVisible(nomedia);
            }
        }
    }

    private boolean handleContextItemSelected(MenuItem item, int position) {
        int id = item.getItemId();
        String mediaLocation = mDirectoryAdapter.getMediaLocation(position);
        if (mediaLocation == null)
            return super.onContextItemSelected(item);

        switch (id){
            case R.id.directory_view_play:
                openMediaFile(position);
                return true;
            case R.id.directory_view_append:
                AudioServiceController.getInstance().append(mediaLocation);
                return true;
            case R.id.directory_view_delete:
                AlertDialog alertDialog = CommonDialogs.deleteMedia(getActivity(), mediaLocation,
                        new VLCRunnable() {
                            @Override
                            public void run(Object o) {
                                refresh();
                            }
                        });
                alertDialog.show();
                return true;
            case R.id.directory_view_play_audio:
                AudioServiceController.getInstance().load(mediaLocation, true);
                return true;
            case  R.id.directory_view_play_video:
                VideoPlayerActivity.start(getActivity(), mediaLocation);
                return true;
            case R.id.directory_view_hide_media:
                DirectoryAdapter.Node folder = (DirectoryAdapter.Node) mDirectoryAdapter.getItem(position);
                try {
                    new File(mDirectoryAdapter.getmCurrentDir()+"/"+folder.name+"/.nomedia").createNewFile();
                    updateLib();
                } catch (IOException e) {}
                return true;
            case R.id.directory_view_show_media:
                DirectoryAdapter.Node folderToShow = (DirectoryAdapter.Node) mDirectoryAdapter.getItem(position);
                new File(mDirectoryAdapter.getmCurrentDir()+"/"+folderToShow.name+"/.nomedia").delete();
                updateLib();
                return true;
        }
        return false;
    }

    private void updateLib() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment fragment = fm.findFragmentByTag(SidebarAdapter.SidebarEntry.ID_AUDIO);
        if (fragment != null) {
            ft.remove(fragment);
            ((BrowserFragment)fragment).clear();
        }
        fragment = fm.findFragmentByTag(SidebarAdapter.SidebarEntry.ID_VIDEO);
        if (fragment != null) {
            ft.remove(fragment);
            ((BrowserFragment)fragment).clear();
        }
        if (!ft.isEmpty())
            ft.commit();
        MediaLibrary.getInstance().loadMediaItems();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(!getUserVisibleHint()) return super.onContextItemSelected(item);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if (info != null && handleContextItemSelected(item, info.position))
            return true;

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int success = mDirectoryAdapter.browse(position);

        if(success < 0) /* Clicked on a media file */
            openMediaFile(position);
        else
            mListView.setSelection(success);

    }

    public boolean isRootDirectory () {
        return mDirectoryAdapter.isRoot();
    }

    public void showParentDirectory() {
        int success = mDirectoryAdapter.browse("..");

        if(success >= 0)
            mListView.setSelection(success);
    };

    private void openMediaFile(int p) {
        AudioServiceController audioController = AudioServiceController.getInstance();
        String mediaFile = mDirectoryAdapter.getMediaLocation(p);

        try {
            final LibVLC libVLC = VLCInstance.get();
            if (!libVLC.hasVideoTrack(mediaFile)) {
                List<String> mediaLocations = mDirectoryAdapter.getAllMediaLocations();
                audioController.load(mediaLocations, mediaLocations.indexOf(mediaFile));
            } else {
                VideoPlayerActivity.start(getActivity(), mediaFile);
            }
        } catch (IOException e) {
            /* disk error maybe? */
        }
    }

    public void sortBy(int sortby) {
        // TODO
        Util.toaster(getActivity(), R.string.notavailable);
    }

    @Override
    public void refresh() {
        if (mDirectoryAdapter != null) {
            mDirectoryAdapter.refresh();
            focusHelper(mDirectoryAdapter.getCount() == 0);
        } else
            focusHelper(true);
        mSwipeRefreshLayout.setRefreshing(false);
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

    DirectoryAdapter.ContextPopupMenuListener mContextPopupMenuListener
        = new DirectoryAdapter.ContextPopupMenuListener() {

            @Override
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            public void onPopupMenu(View anchor, final int position) {
                if (!LibVlcUtil.isHoneycombOrLater()) {
                    // Call the "classic" context menu
                    anchor.performLongClick();
                    return;
                }
                PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
                if (mDirectoryAdapter.isChildFile(position))
                    popupMenu.getMenuInflater().inflate(R.menu.directory_view_file, popupMenu.getMenu());
                else {
                    DirectoryAdapter.Node folder = (DirectoryAdapter.Node) mDirectoryAdapter.getItem(position);
                    if (Util.canWrite(mDirectoryAdapter.getmCurrentDir()+"/"+folder.name)) {
                        Menu menu = popupMenu.getMenu();
                        popupMenu.getMenuInflater().inflate(R.menu.directory_view_dir, menu);
                        boolean nomedia = new File(mDirectoryAdapter.getmCurrentDir() + "/" + folder.name + "/.nomedia").exists();
                        menu.findItem(R.id.directory_view_hide_media).setVisible(!nomedia);
                        menu.findItem(R.id.directory_view_show_media).setVisible(nomedia);
                    }
                }

                popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return handleContextItemSelected(item, position);
                    }
                });
                popupMenu.show();
            }

    };

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReadyToDisplay)
            display();
        else
            mReadyToDisplay = ready;
    }

    @Override
    public void display() {
        mReadyToDisplay = true;
        refresh();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.directories);
    }

    public void clear(){}
}
