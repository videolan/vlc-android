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

import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MedialibraryUtils;
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.CustomDirectories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class StorageBrowserAdapter extends BaseBrowserAdapter {

    private static List<String> mMediaDirsLocation;
    private static List<String> mCustomDirsLocation;

    StorageBrowserAdapter(BaseBrowserFragment fragment) {
        super(fragment);
        updateMediaDirs();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        MediaLibraryItem storage = getItem(position);

        if (storage.getItemType() == MediaLibraryItem.TYPE_MEDIA) storage = new Storage(((MediaWrapper)storage).getUri());
        String storagePath = ((Storage)storage).getUri().getPath();
        if (!storagePath.endsWith("/")) storagePath += "/";
        boolean hasContextMenu = mCustomDirsLocation.contains(storagePath);
        boolean checked = ((StorageBrowserFragment) fragment).mScannedDirectory || mMediaDirsLocation.contains(storagePath);
        vh.binding.setItem(storage);
        vh.binding.setHasContextMenu(hasContextMenu);
        if (checked)
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_CHECKED);
        else if (hasDiscoveredChildren(storagePath))
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_PARTIAL);
        else
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_UNCHECKED);
        vh.binding.setCheckEnabled(!((StorageBrowserFragment) fragment).mScannedDirectory);
    }

    private boolean hasDiscoveredChildren(String path) {
        for (String directory : mMediaDirsLocation) if (directory.startsWith(path)) return true;
        return false;
    }

    void updateMediaDirs() {
        if (mMediaDirsLocation != null) mMediaDirsLocation.clear();
        final String folders[] = VLCApplication.getMLInstance().getFoldersList();
        mMediaDirsLocation = new ArrayList<>(folders.length);
        for (String folder : folders) {
            mMediaDirsLocation.add(Uri.decode(folder.startsWith("file://") ? folder.substring(7) : folder));
        }
        mCustomDirsLocation = new ArrayList<>(Arrays.asList(CustomDirectories.getCustomDirectories()));
    }

    protected void checkBoxAction(View v, String mrl) {
        final ThreeStatesCheckbox tscb = (ThreeStatesCheckbox) v;
        int state = tscb.getState();
        if (state == ThreeStatesCheckbox.STATE_CHECKED) {
            MedialibraryUtils.addDir(mrl);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(v.getContext().getApplicationContext());
            if (prefs.getInt(Constants.KEY_MEDIALIBRARY_SCAN, -1) != Constants.ML_SCAN_ON) prefs.edit().putInt(Constants.KEY_MEDIALIBRARY_SCAN, Constants.ML_SCAN_ON).apply();
        } else MedialibraryUtils.removeDir(mrl);
        ((StorageBrowserFragment)fragment).processEvent((CheckBox) v, mrl);
    }
}
