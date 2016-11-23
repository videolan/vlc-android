/*
 * *************************************************************************
 *  MediaBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015-2016 VLC authors and VideoLAN
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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MediaInfoDialog;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.util.FileUtils;

import java.util.LinkedList;

public abstract class MediaBrowserFragment extends PlaybackServiceFragment implements android.support.v7.view.ActionMode.Callback {

    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected volatile boolean mReadyToDisplay = true;
    protected Medialibrary mMediaLibrary;
    protected ActionMode mActionMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mSwipeRefreshLayout != null)
            mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
    }

    public void onStart(){
        super.onStart();
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(getSubTitle());
            getActivity().supportInvalidateOptionsMenu();
        }
    }

    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReadyToDisplay)
            display();
        else
            mReadyToDisplay = ready;
    }


    protected abstract String getTitle();
    public abstract void onRefresh();

    protected String getSubTitle() { return null; }
    public void clear() {}
    protected void display() {}

    protected void inflate(Menu menu, int position) {}
    protected void setContextMenuItems(Menu menu, int position) {}
    protected boolean handleContextItemSelected(MenuItem menu, int position) { return false;}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null)
            return;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo)menuInfo;
        inflate(menu, info.position);

        setContextMenuItems(menu, info.position);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if(!getUserVisibleHint())
            return false;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo) menu.getMenuInfo();

        return info != null && handleContextItemSelected(menu, info.position);
    }

    protected void deleteMedia(final MediaLibraryItem mw, final boolean refresh) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final LinkedList<String> foldersToReload = new LinkedList<>();
                final LinkedList<String> mediaPaths = new LinkedList<>();
                for (MediaWrapper media : mw.getTracks(mMediaLibrary)) {
                    String path = media.getUri().getPath();
                    mediaPaths.add(media.getLocation());
                    String parentPath = FileUtils.getParent(path);
                    if (FileUtils.deleteFile(path) && media.getId() > 0L && !foldersToReload.contains(parentPath))
                        foldersToReload.add(parentPath);
                }
                for (String folder : foldersToReload)
                        mMediaLibrary.reload(folder);
                if (mService != null && getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            for (String path : mediaPaths)
                                mService.removeLocation(path);
                            if (refresh)
                                onRefresh();
                        }
                    });
                }
            }
        });
    }

    protected void showInfoDialog(MediaWrapper media) {
        BottomSheetDialogFragment bottomSheetDialogFragment = new MediaInfoDialog();
        Bundle args = new Bundle();
        args.putParcelable(MediaInfoDialog.ITEM_KEY, media);
        bottomSheetDialogFragment.setArguments(args);
        bottomSheetDialogFragment.show(getFragmentManager(), bottomSheetDialogFragment.getTag());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void startActionMode() {
        mActionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(this);
    }

    protected void stopActionMode() {
        if (mActionMode != null)
            mActionMode.finish();
    }

    public void invalidateActionMode() {
        if (mActionMode != null)
            mActionMode.invalidate();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }
}
