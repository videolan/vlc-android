/*
 * *************************************************************************
 *  VideoBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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

import android.os.Bundle;

import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.media.Thumbnailer;

import java.util.ArrayList;

public class VideoBrowserFragment extends SortedBrowserFragment {

    protected static Thumbnailer sThumbnailer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sThumbnailer = MainTvActivity.getThumbnailer();
    }

    public void onResume() {
        super.onResume();
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(this);
    }

    public void onPause() {
        super.onPause();
        /* unregister from thumbnailer */
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(null);
    }

    @Override
    public void refresh() {
        mMediaIndex.clear();
        super.refresh();
    }

    @Override
    protected void browse() {
        ArrayList<MediaWrapper> videos = MediaLibrary.getInstance().getVideoItems();
        MediaWrapper media;
        for (int i = 0 ; i < videos.size() ; ++i) {
            media = videos.get(i);
            String letter = media.getTitle().substring(0, 1).toUpperCase();
            if (mMediaItemMap.containsKey(letter)){
                mMediaItemMap.get(letter).mediaList.add(media);
            } else {
                ListItem item = new ListItem(letter, media);
                mMediaItemMap.put(letter, item);
            }
            mMediaIndex.put(media.getLocation(), Integer.valueOf(i));
        }
        sort();
    }
}
