/*
 * *************************************************************************
 *  MediaLibBrowserFragment.java
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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.tv.TvUtil;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class MediaLibBrowserFragment extends GridFragment implements OnItemViewSelectedListener {
    protected Medialibrary mMediaLibrary;
    private BackgroundManager mBackgroundManager;
    private Object mSelectedItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.setAutoReleaseOnStop(false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setOnItemViewSelectedListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mSelectedItem != null)
            TvUtil.updateBackground(mBackgroundManager, mSelectedItem);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mBackgroundManager.isAttached())
            mBackgroundManager.attachToView(getView());
    }

    @Override
    public void onPause() {
        super.onPause();
        TvUtil.releaseBackgroundManager(mBackgroundManager);
    }

    public void refresh() {
        mMediaLibrary.reload();
    }

    public void updateList() {}

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                               RowPresenter.ViewHolder rowViewHolder, Row row) {
        mSelectedItem = item;
        TvUtil.updateBackground(mBackgroundManager, item);
    }
}
