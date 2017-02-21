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

import android.content.Intent;
import android.view.View;
import android.widget.CheckBox;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.CustomDirectories;

import java.util.ArrayList;
import java.util.Arrays;

class StorageBrowserAdapter extends BaseBrowserAdapter {

    private static ArrayList<String> mMediaDirsLocation;
    private static ArrayList<String> mCustomDirsLocation;

    StorageBrowserAdapter(BaseBrowserFragment fragment) {
        super(fragment);
        if (mMediaDirsLocation == null && mCustomDirsLocation == null)
            updateMediaDirs();
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
        vh.binding.setChecked(((StorageBrowserFragment) fragment).mScannedDirectory || mMediaDirsLocation.contains(storagePath));
        vh.binding.setCheckEnabled(!((StorageBrowserFragment) fragment).mScannedDirectory);
        if (hasContextMenu)
            vh.setContextMenuListener();
    }

    public void addItem(MediaLibraryItem item, boolean top) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA)
             item = new Storage(((MediaWrapper)item).getUri());
        super.addItem(item, top);
    }

    private void removeDir(final String path) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                VLCApplication.getMLInstance().removeFolder(path);
            }
        });
    }

    private void addDir(final String path) {
        Intent intent = new Intent(MediaParsingService.ACTION_DISCOVER, null, VLCApplication.getAppContext(), MediaParsingService.class);
        intent.putExtra(MediaParsingService.EXTRA_PATH, path);
        VLCApplication.getAppContext().startService(intent);
    }

    void updateMediaDirs() {
        if (mMediaDirsLocation != null)
            mMediaDirsLocation.clear();
        String folders[] = VLCApplication.getMLInstance().getFoldersList();
        mMediaDirsLocation = new ArrayList<>(folders.length);
        for (String folder : folders) {
            mMediaDirsLocation.add(folder.substring(7));
        }
        mCustomDirsLocation = new ArrayList<>(Arrays.asList(CustomDirectories.getCustomDirectories()));
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
        ((StorageBrowserFragment)fragment).processEvent((CheckBox) v, path);
    }
}
