/*
 * *************************************************************************
 *  StorageBrowserAdapter.java
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

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Arrays;

public class StorageBrowserAdapter extends BaseBrowserAdapter {

    boolean isRoot;
    ArrayList<String> mMediaDirsLocation = new ArrayList<>();
    ArrayList<String> mCustomDirsLocation;

    public StorageBrowserAdapter(BaseBrowserFragment fragment) {
        super(fragment);
        updateMediaDirs();
        isRoot = fragment.isRootDirectory();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewHolder vh;
        View v;
        v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.directory_view_item, parent, false);
        vh = new MediaViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final MediaLibraryItem storage = getItem(position);
        String storagePath = ((Storage)storage).getUri().getPath();
        if (!storagePath.endsWith("/"))
            storagePath += "/";
        boolean hasContextMenu = mCustomDirsLocation.contains(storagePath);
        vh.binding.setItem(storage);
        vh.binding.setHasContextMenu(hasContextMenu);
        vh.binding.setChecked(((StorageBrowserFragment) fragment).mScannedDirectory ||
                (isRoot && Util.isListEmpty(mMediaDirsLocation)) || mMediaDirsLocation.contains(storagePath));
        vh.binding.setCheckEnabled(!((StorageBrowserFragment) fragment).mScannedDirectory);
        if (hasContextMenu)
            vh.setContextMenuListener();
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        vh.binding.setItem(null);
    }

    public void addItem(MediaLibraryItem item, boolean notify, boolean top) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA)
             item = new Storage(((MediaWrapper)item).getUri());
        super.addItem(item, notify, top);
    }

    private void removeDir(final String path) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                //if media dir list was empty, we add all others
                if (mMediaDirsLocation.isEmpty()) {
                    Storage storage;
                    String pathString;
                    for (Object item : mMediaList) {
                        storage = (Storage) item;
                        if (!TextUtils.equals(path, storage.getUri().getPath())) {
                            pathString = storage.getUri().getPath();
                            VLCApplication.getMLInstance().discover(pathString);
                        }
                    }
                } else {
                    VLCApplication.getMLInstance().removeFolder(path);
                }
                updateMediaDirs();
                if (isRoot && mMediaDirsLocation.isEmpty() && getItemCount() > 1)
                    refreshFragment();
            }
        });
    }

    private void addDir(final String path) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                Medialibrary ml = VLCApplication.getMLInstance();
                ml.discover(path);
                //No need to check for parents for now
                String parentPath = FileUtils.getParent(path);
                while (parentPath != null && !TextUtils.equals(parentPath, "/")) {
                    ml.removeFolder(parentPath);
                    parentPath = FileUtils.getParent(parentPath);
                }
                //Remove subfolders, it would be redundant
                for (String customDirPath : mMediaDirsLocation) {
                    if (customDirPath.startsWith(path + "/"))
                        ml.removeFolder(customDirPath);
                }
                updateMediaDirs();
            }
        });
    }

    void updateMediaDirs(){
        mMediaDirsLocation.clear();
        mMediaDirsLocation = new ArrayList<>(Arrays.asList(VLCApplication.getMLInstance().getFoldersList()));
        mCustomDirsLocation = new ArrayList<>(Arrays.asList(CustomDirectories.getCustomDirectories()));
    }

    void refreshFragment(){
        fragment.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Util.isListEmpty(mMediaDirsLocation))
                    fragment.refresh();
            }
        });
    }

    protected void openMediaFromView(MediaViewHolder holder, View v) {
        MediaWrapper mw = new MediaWrapper(((Storage) getItem(holder.getAdapterPosition())).getUri());
        mw.setType(MediaWrapper.TYPE_DIR);
        fragment.browse(mw, holder.getAdapterPosition(), holder.binding.browserCheckbox.isChecked());
    }

    protected void checkBoxAction(View v, String path){
            boolean isChecked = ((CheckBox) v).isChecked();
            if (isChecked)
                addDir(path);
            else
                removeDir(path);
    }
}
