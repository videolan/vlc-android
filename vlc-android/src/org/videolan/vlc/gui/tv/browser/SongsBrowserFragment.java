/*
 * ************************************************************************
 *  SongsBrowserFragment.java
 * *************************************************************************
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;

import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SongsBrowserFragment extends SortedBrowserFragment {

    private ArrayList<MediaWrapper> mSongs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void browse() {
        mSongs = MediaLibrary.getInstance().getAudioItems();
        MediaWrapper media;
        for (int i = 0 ; i < mSongs.size() ; ++i) {
            media = mSongs.get(i);
            addMedia(media);
            mMediaIndex.put(media.getLocation(), i);
        }
        sort();
    }

    protected void sort(){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                Collections.sort(mSongs, MediaComparators.byName);
                mMediaItemMap = new TreeMap<>(mMediaItemMap); //sort sections
                for (ListItem item : mMediaItemMap.values()) {
                    Collections.sort(item.mediaList, MediaComparators.byName);
                }
                mHandler.sendEmptyMessage(UPDATE_DISPLAY);
            }
        });
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder viewHolder, final Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                int position = 0;
                String location = ((MediaWrapper)item).getLocation();
                for (int i = 0; i < mSongs.size(); ++i) {
                    if (TextUtils.equals(location, mSongs.get(i).getLocation())) {
                        position = i;
                        break;
                    }
                }
                TvUtil.playAudioList(getActivity(), mSongs, position);
            }
        });
    }
}
