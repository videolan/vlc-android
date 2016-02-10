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
import android.os.Build;
import android.text.TextUtils;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;

import java.io.File;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class DirectoryBrowserFragment extends MediaSortedFragment{

    public static final String TAG = "VLC/NetworkBrowserFragment";

    protected void browseRoot() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                String storages[] = AndroidDevices.getMediaDirectories();
                MediaWrapper directory;
                for (String mediaDirLocation : storages) {
                    if (!(new File(mediaDirLocation).exists()))
                        continue;
                    directory = new MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation));
                    directory.setType(MediaWrapper.TYPE_DIR);
                    if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                        directory.setDisplayTitle(getString(R.string.internal_memory));
                    addMedia(directory);
                }
                sort();
            }
        });
    }
}
