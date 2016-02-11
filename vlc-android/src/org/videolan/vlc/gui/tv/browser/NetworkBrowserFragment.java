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

import org.videolan.libvlc.Media;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.Util;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class NetworkBrowserFragment extends MediaSortedFragment {

    public static final String TAG = "VLC/NetworkBrowserFragment";

    protected void browseRoot() {
        mMediaBrowser.discoverNetworkShares();
    }

    protected void addMedia(Media media){
        MediaWrapper mw = new MediaWrapper(media);
        if (mUri == null)
            mw.setDescription(mw.getUri().getScheme());
        int type = mw.getType();
        if (type != MediaWrapper.TYPE_AUDIO && type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_DIR)
            return;
        String letter = mw.getTitle().substring(0, 1).toUpperCase();
        if (mMediaItemMap.containsKey(letter)){
            mMediaItemMap.get(letter).mediaList.add(mw);
        } else {
            ListItem item = new ListItem(letter, mw);
            mMediaItemMap.put(letter, item);
        }
    }
}
