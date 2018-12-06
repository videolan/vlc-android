/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
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

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.viewmodels.browser.BrowserModel;
import org.videolan.vlc.viewmodels.browser.BrowserModelKt;

import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class DirectoryBrowserFragment extends MediaSortedFragment<BrowserModel> {

    public static final String TAG = "VLC/DirectoryBrowserFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this, new BrowserModel.Factory(requireContext(), mUri.toString(), BrowserModelKt.TYPE_FILE, mShowHiddenFiles)).get(BrowserModel.class);
        viewModel.getCategories().observe(this, new Observer<Map<String, List<MediaLibraryItem>>>() {
            @Override
            public void onChanged(@Nullable Map<String, List<MediaLibraryItem>> stringListMap) {
                if (stringListMap != null) update(stringListMap);
            }
        });
        ExternalMonitor.INSTANCE.getStorageUnplugged().observe(this, new Observer<Uri>() {
            @Override
            public void onChanged(Uri uri) {
                if (mUri != null && "file".equals(mUri.getScheme())) {
                    final String currentPath = mUri.getPath();
                    final String unpluggedPath = uri.getPath();
                    if (currentPath != null && unpluggedPath != null && currentPath.startsWith(unpluggedPath)) {
                        final Activity activity = getActivity();
                        if (activity != null) activity.finish();
                    }
                }
            }
        });
    }
}
