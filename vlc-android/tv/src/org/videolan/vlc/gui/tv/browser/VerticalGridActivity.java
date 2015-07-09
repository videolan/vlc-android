/*****************************************************************************
 * VerticalGridActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv.browser;

import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.SearchActivity;

public class VerticalGridActivity extends BaseTvActivity implements GridFragment.BrowserActivity {

    GridFragment mFragment;
    ProgressBar mContentLoadingProgressBar;
    TextView mEmptyView;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_vertical_grid);
        mContentLoadingProgressBar = (ProgressBar) findViewById(R.id.tv_fragment_progress);
        mEmptyView = (TextView) findViewById(R.id.tv_fragment_empty);
        getWindow().setBackgroundDrawableResource(R.drawable.background);
        long type = getIntent().getLongExtra(MainTvActivity.BROWSER_TYPE, -1);
        if (type == MainTvActivity.HEADER_VIDEO)
                mFragment = new VideoGridFragment();
        else if (type == MainTvActivity.HEADER_CATEGORIES)
                mFragment = new MusicFragment();
        else if (type == MainTvActivity.HEADER_NETWORK)
                mFragment = new BrowserGridFragment();
        else {
            finish();
            return;
        }
        getFragmentManager().beginTransaction()
                .add(R.id.tv_fragment_placeholder, mFragment)
                .commit();
    }

    @Override
    protected void refresh() {
        mFragment.refresh();
    }

    @Override
    protected void updateList() {
        mFragment.updateList();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (mFragment instanceof BrowserGridFragment && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_Y)) {
            ((BrowserGridFragment)mFragment).showDetails();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void showProgress(boolean show){
        mContentLoadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void updateEmptyView(boolean empty) {
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
