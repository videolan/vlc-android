/*
 * *************************************************************************
 *  StorageBrowserFragment.java
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

import android.arch.lifecycle.ViewModelProviders;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.SimpleArrayMap;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.CheckBox;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemBinding;
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.viewmodels.browser.StorageProvider;

public class StorageBrowserFragment extends FileBrowserFragment implements EntryPointsEventsCb {

    public static final String KEY_IN_MEDIALIB = "key_in_medialib";

    boolean mScannedDirectory = false;
    private final SimpleArrayMap<String, CheckBox> mProcessingFolders = new SimpleArrayMap<>();
    private Snackbar mSnack;

    public boolean isSortEnabled() {
        return false;
    }

    @Override
    protected Fragment createFragment() {
        return new StorageBrowserFragment();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mAdapter = new StorageBrowserAdapter(this);
        if (bundle == null) bundle = getArguments();
        if (bundle != null) mScannedDirectory = bundle.getBoolean(KEY_IN_MEDIALIB);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mRoot && VLCApplication.showTvUi()) {
            mSnack = Snackbar.make(view, R.string.tv_settings_hint, Snackbar.LENGTH_INDEFINITE);
            if (AndroidUtil.isLolliPopOrLater) mSnack.getView().setElevation(view.getResources().getDimensionPixelSize(R.dimen.audio_player_elevation));
        }
    }

    protected void setupBrowser() {
        mProvider = ViewModelProviders.of(this, new StorageProvider.Factory(mMrl, mShowHiddenFiles)).get(StorageProvider.class);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mRoot && mFabPlay != null) {
            mFabPlay.setImageResource(R.drawable.ic_fab_add);
            mFabPlay.setOnClickListener(this);
            setFabPlayVisibility(true);
        }
        VLCApplication.getMLInstance().addEntryPointsEventsCb(this);
        if (mSnack != null) mSnack.show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mFabPlay != null) {
            mFabPlay.setVisibility(View.GONE);
            mFabPlay.setOnClickListener(null);
        }
        VLCApplication.getMLInstance().removeEntryPointsEventsCb(this);
        if (mSnack != null) mSnack.dismiss();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory);
    }

//    @Override
//    public void onMediaAdded(int index, Media media) {
//        if (media.getType() != Media.Type.Directory)
//            return;
//        super.onMediaAdded(index, media);
//    }

    public void browse (MediaWrapper media, int position, boolean scanned){
        final FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        final Fragment next = createFragment();
        final Bundle args = new Bundle();
        args.putParcelable(KEY_MEDIA, media);
        args.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory || scanned);
        next.setArguments(args);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        ft.addToBackStack(mMrl);
        ft.commit();
    }

    protected void setContextMenuItems(MenuInflater inflater, Menu menu, int position) {
        if (mRoot) {
            Storage storage = (Storage) mAdapter.getItem(position);
            boolean isCustom = CustomDirectories.contains(storage.getUri().getPath());
            if (isCustom)
                inflater.inflate(R.menu.directory_custom_dir, menu);
        } else
            super.setContextMenuItems(menu, position);
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        MediaWrapper mw = new MediaWrapper(((Storage) item).getUri());
        mw.setType(MediaWrapper.TYPE_DIR);
        browse(mw, position, ((BrowserItemBinding)DataBindingUtil.findBinding(v)).browserCheckbox.getState() == ThreeStatesCheckbox.STATE_CHECKED);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab){
            showAddDirectoryDialog();
        }
    }

    void processEvent(CheckBox cbp, String mrl) {
        cbp.setEnabled(false);
        mProcessingFolders.put(mrl, cbp);
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories_summary);
    }

    @Override
    public void onEntryPointBanned(String entryPoint, boolean success) {}

    @Override
    public void onEntryPointUnbanned(String entryPoint, boolean success) {}

    @Override
    public void onEntryPointRemoved(String entryPoint, final boolean success) {
        if (entryPoint.endsWith("/"))
            entryPoint = entryPoint.substring(0, entryPoint.length()-1);
        if (mProcessingFolders.containsKey(entryPoint)) {
            final CheckBox cb = mProcessingFolders.remove(entryPoint);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.setEnabled(true);
                    if (success) {
                        ((StorageBrowserAdapter)mAdapter).updateMediaDirs();
                        mAdapter.notifyDataSetChanged();
                    } else
                        cb.setChecked(true);
                }
            });
        }
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        String path = entryPoint;
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        if (mProcessingFolders.containsKey(path)) {
            final String finalPath = path;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mProcessingFolders.get(finalPath).setEnabled(true);
                }
            });
            ((StorageBrowserAdapter)mAdapter).updateMediaDirs();
        }
    }
}
