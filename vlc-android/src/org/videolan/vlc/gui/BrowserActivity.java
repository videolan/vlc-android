/*****************************************************************************
 * BrowserActivity.java
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

package org.videolan.vlc.gui;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Stack;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;

public class BrowserActivity extends ListActivity {
    public final static String TAG = "VLC/BrowserActivity";

    /**
     * TODO:
     */

    private BrowserAdapter mAdapter;
    private File mCurrentDir;
    private final Stack<ScrollState> mScrollStates = new Stack<ScrollState>();
    private String mRoots[];

    private class ScrollState {
        public ScrollState(int index, int top) {
            this.index = index;
            this.top = top;
        }

        int index;
        int top;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.browser);
        mAdapter = new BrowserAdapter(this);
        setListAdapter(mAdapter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        registerReceiver(messageReceiver, filter);

        refreshRoots();
        openStorageDevices(mRoots);

        registerForContextMenu(getListView());
    }

    private void refreshRoots() {
        ArrayList<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(Util.getStorageDirectories()));
        list.addAll(Arrays.asList(Util.getCustomDirectories()));
        mRoots = list.toArray(new String[list.size()]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(messageReceiver);
        mAdapter.clear();
        mScrollStates.clear();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        int position = ((AdapterContextMenuInfo)menuInfo).position;
        final File item = mAdapter.getItem(position);
        if (mCurrentDir != null
                || item.getPath().equals(BrowserAdapter.ADD_ITEM_PATH)
                || Arrays.asList(Util.getStorageDirectories()).contains(
                        item.getPath())) {
            return;
        }

        MenuItem delete = menu.add(R.string.remove_custom_path);
        delete.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem arg0) {
                // remove any checkmarks of the custom item
                final MediaDatabase dbManager = MediaDatabase.getInstance(BrowserActivity.this);
                for(File f : dbManager.getMediaDirs()) {
                    if(f.getPath().startsWith(item.getPath()))
                        dbManager.removeDir(f.getPath());
                }
                Util.removeCustomDirectory(item.getPath());
                refresh();
                return true;
            }
        });
    }

    private void openStorageDevices(String roots[]) {
        mCurrentDir = null;
        mAdapter.clear();
        for (String s : roots) {
            File f = new File(s);
            if (f.exists())
                mAdapter.add(f);
        }
        mAdapter.add(new File(BrowserAdapter.ADD_ITEM_PATH));
        mAdapter.sort();

        // set scroll position to top
        getListView().setSelection(0);
    }

    private void openDir(File file) {
        if(file == null || !file.exists() || file.getPath() == null
                || file.getPath().equals(BrowserAdapter.ADD_ITEM_PATH))
            return;

        mAdapter.clear();
        mCurrentDir = file;
        File[] files = file.listFiles(new DirFilter());
        /* If no sub-directories or I/O error don't crash */
        if(files == null || files.length < 1) {
            Util.toaster(this, R.string.nosubdirectory);
            this.finish();
            return;
        }
        for (int i = 0; i < files.length; i++) {
            mAdapter.add(files[i]);
        }
        mAdapter.sort();
        // set scroll position to top
        getListView().setSelection(0);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        File file = mAdapter.getItem(position);
        if(file.getPath().equals(BrowserAdapter.ADD_ITEM_PATH)) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            b.setTitle(R.string.add_custom_path);
            b.setMessage(R.string.add_custom_path_description);
            b.setView(input);
            b.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface x, int y) {return;}
            });
            b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Util.addCustomDirectory(input.getText().toString());
                    refresh();
                }
            });
            b.show();
            return;
        }

        File[] files = file.listFiles(new DirFilter());
        if (files != null && files.length > 0) {
            // store scroll state
            int index = l.getFirstVisiblePosition();
            int top = l.getChildAt(0).getTop();
            mScrollStates.push(new ScrollState(index, top));
            openDir(file);
        } else {
            Util.toaster(this, R.string.nosubdirectory);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mCurrentDir == null) {
                // We're on the list of storage devices
                return super.onKeyDown(keyCode, event);
            }

            // Check if we are on one of the root
            boolean isRoot = false;
            for (String root: mRoots) {
                if (mCurrentDir.getPath().equals(root)) {
                    isRoot = true;
                    break;
                }
            }

            if (isRoot) {
                openStorageDevices(mRoots);
                return true;
            } else {
                openDir(mCurrentDir.getParentFile());
                // restore scroll state
                if (mScrollStates.size() > 0) {
                    ScrollState ss = mScrollStates.pop();
                    getListView().setSelectionFromTop(ss.index, ss.top);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void refresh() {
        if (mCurrentDir == null) {
            refreshRoots();
            openStorageDevices(mRoots);
        } else {
            openDir(mCurrentDir);
        }
        mAdapter.notifyDataSetChanged();
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

    /**
     * Filter: accept only directories
     */
    private class DirFilter implements FileFilter {

        @Override
        public boolean accept(File f) {
            return f.isDirectory() && !Media.FOLDER_BLACKLIST.contains(f.getPath().toLowerCase(Locale.ENGLISH));
        }
    }

}
