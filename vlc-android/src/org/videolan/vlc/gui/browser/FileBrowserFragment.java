/*
 * *************************************************************************
 *  FileBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.MediaDatabase;
import org.videolan.libvlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.CustomDirectories;

import java.io.File;

public class FileBrowserFragment extends BaseBrowserFragment {

    private AlertDialog mAlertDialog;

    public FileBrowserFragment() {
        super();
        ROOT = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mRoot = mMrl == null;
    }

    @Override
    protected Fragment createFragment() {
        return new FileBrowserFragment();
    }

    public String getTitle(){
        if (mRoot)
            return getCategoryTitle();
        else {
            String title = mMrl;
            if (mCurrentMedia != null) {
                if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mMrl))
                    title = getString(R.string.internal_memory);
                else
                    title = mCurrentMedia.getTitle();
            }
            return title;
        }
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories);
    }

    @Override
    protected void browseRoot() {
        final Activity context = getActivity();
        mAdapter.updateMediaDirs();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String storages[] = AndroidDevices.getMediaDirectories();
                MediaWrapper directory;
                for (String mediaDirLocation : storages) {
                    if (!(new File(mediaDirLocation).exists()))
                        continue;
                    directory = new MediaWrapper(mediaDirLocation);
                    directory.setType(MediaWrapper.TYPE_DIR);
                    if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                        directory.setTitle(getString(R.string.internal_memory));
                    mAdapter.addItem(directory, false, false);
                }
                mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
                if (mReadyToDisplay) {
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateEmptyView();
                            mAdapter.notifyDataSetChanged();
                            parseSubDirectories();
                        }
                    });
                }
            }
        }).start();
    }

    public void onStart(){
        super.onStart();

        //Handle network connection state
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        getActivity().registerReceiver(storageReceiver, filter);
        if (mReadyToDisplay)
            update();
    }

    @Override
    protected void updateDisplay() {
        super.updateDisplay();
        if (isRootDirectory())
            mAdapter.updateMediaDirs();
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(storageReceiver);
        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();
    }

    public void showAddDirectoryDialog() {
        final Context context = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AppCompatEditText input = new AppCompatEditText(context);
        if (!LibVlcUtil.isHoneycombOrLater()) {
            input.setTextColor(getResources().getColor(R.color.grey50));
        }
        input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        builder.setTitle(R.string.add_custom_path);
        builder.setMessage(R.string.add_custom_path_description);
        builder.setView(input);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                return;
            }
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String path = input.getText().toString().trim();
                File f = new File(path);
                if (!f.exists() || !f.isDirectory()) {
                    Toast.makeText(context, getString(R.string.directorynotfound, path), Toast.LENGTH_SHORT).show();
                    return;
                }

                CustomDirectories.addCustomDirectory(f.getAbsolutePath());
                refresh();
                updateLib();
            }
        });
        mAlertDialog = builder.show();
    }

    @Override
    protected boolean handleContextItemSelected(MenuItem item, int position) {
        if (mRoot) {
            if (item.getItemId() == R.id.directory_remove_custom_path){
                BaseBrowserAdapter.Storage storage = (BaseBrowserAdapter.Storage) mAdapter.getItem(position);
                MediaDatabase.getInstance().recursiveRemoveDir(storage.getPath());
                CustomDirectories.removeCustomDirectory(storage.getPath());
                mAdapter.updateMediaDirs();
                mAdapter.removeItem(position, true);
                updateLib();
                return true;
            } else
                return false;
        } else
            return super.handleContextItemSelected(item, position);
    }

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_REMOVED) ||
                    action.equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)) {
                if (mReadyToDisplay)
                    update();
            }
        }
    };
}
