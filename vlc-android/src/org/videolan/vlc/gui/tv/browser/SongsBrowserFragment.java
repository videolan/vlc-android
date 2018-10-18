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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.os.Build;
import android.os.Bundle;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import android.text.TextUtils;

import org.jetbrains.annotations.Nullable;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.audio.TracksModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SongsBrowserFragment extends CategoriesFragment<TracksModel> {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(this, new TracksModel.Factory(requireContext(), null)).get(TracksModel.class);
        viewModel.getCategories().observe(this, new Observer<Map<String, List<MediaLibraryItem>>>() {
            @Override
            public void onChanged(@Nullable Map<String, List<MediaLibraryItem>> stringListMap) {
                if (stringListMap != null) update(stringListMap);
            }
        });
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder viewHolder, final Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                int position = 0;
                String location = ((MediaWrapper)item).getLocation();
                final ArrayList<MediaWrapper> songs = (ArrayList<MediaWrapper>)(ArrayList<?>) viewModel.getDataset().getValue();
                for (int i = 0; i < songs.size(); ++i) {
                    if (TextUtils.equals(location, songs.get(i).getLocation())) {
                        position = i;
                        break;
                    }
                }
                TvUtil.INSTANCE.playAudioList(getActivity(), songs, position);
            }
        });
    }
}
