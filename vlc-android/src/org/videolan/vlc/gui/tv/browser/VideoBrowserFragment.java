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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;

import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.media.Thumbnailer;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class VideoBrowserFragment extends SortedBrowserFragment {

    protected static Thumbnailer sThumbnailer;
    private ArrayList<MediaWrapper> mVideos;

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
    protected void browseRoot() {
        browse();
    }

    @Override
    protected void browse() {
        mVideos = MediaLibrary.getInstance().getVideoItems();
        MediaWrapper media;
        for (int i = 0 ; i < mVideos.size() ; ++i) {
            media = mVideos.get(i);
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

    @Override
    public void onItemClicked(Presenter.ViewHolder viewHolder, Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
        MediaWrapper media = (MediaWrapper) item;
        MediaUtils.openList(getActivity(), mVideos, mMediaIndex.get(media.getLocation()));
    }
}
