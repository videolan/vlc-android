/*****************************************************************************
 * VerticalGridActivity.java
 *****************************************************************************
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
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

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment;
import org.videolan.vlc.interfaces.Sortable;
import org.videolan.vlc.util.Constants;

import androidx.fragment.app.Fragment;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class VerticalGridActivity extends BaseTvActivity implements BrowserActivityInterface {

    private static final String TAG = "VLC/VerticalGridActivity";
    private static final int GRID_LIMIT = 24;
    BrowserFragmentInterface mFragment;
    ProgressBar mContentLoadingProgressBar;
    TextView mEmptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_vertical_grid);
        mContentLoadingProgressBar = findViewById(R.id.tv_fragment_progress);
        mEmptyView = findViewById(R.id.tv_fragment_empty);
        if (savedInstanceState == null) {
            long type = getIntent().getLongExtra(MainTvActivity.BROWSER_TYPE, -1);
            if (type == Constants.HEADER_VIDEO) {
                final String group = getIntent().getStringExtra(Constants.KEY_GROUP);
                if (group != null) {
                    mFragment = new VideosFragment();
                    final Bundle args = new Bundle(1);
                    args.putString(Constants.KEY_GROUP, group);
                    ((Fragment)mFragment).setArguments(args);
                } else if (VLCApplication.getMLInstance().getVideoCount() > GRID_LIMIT) mFragment = new VideoBrowserFragment();
                else mFragment = new VideosFragment();
            }
            else if (type == Constants.HEADER_CATEGORIES) {
                final long audioCategory = getIntent().getLongExtra(Constants.AUDIO_CATEGORY, Constants.CATEGORY_SONGS);
                if (audioCategory == Constants.CATEGORY_SONGS &&
                        VLCApplication.getMLInstance().getAudioCount() > GRID_LIMIT) {
                    mFragment = new SongsBrowserFragment();
                } else {
                    if (audioCategory == Constants.CATEGORY_ARTISTS) mFragment = new ArtistsFragment();
                    else if (audioCategory == Constants.CATEGORY_ALBUMS) mFragment = new AlbumsFragment();
                    else if (audioCategory == Constants.CATEGORY_GENRES) mFragment = new GenresFragment();
                    else mFragment = new MusicFragment();
                    final Bundle args = new Bundle();
                    args.putParcelable(Constants.AUDIO_ITEM, getIntent().getParcelableExtra(Constants.AUDIO_ITEM));
                    ((Fragment) mFragment).setArguments(args);
                }
            } else if (type == Constants.HEADER_NETWORK) {
                Uri uri = getIntent().getData();
                if (uri == null) uri = getIntent().getParcelableExtra(Constants.KEY_URI);
                if (uri == null) mFragment = new BrowserGridFragment();
                else mFragment = new NetworkBrowserFragment();
            } else if (type == Constants.HEADER_DIRECTORIES) {
                mFragment = new DirectoryBrowserFragment();
            }  else {
                finish();
                return;
            }
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.tv_fragment_placeholder, ((Fragment)mFragment))
                    .commit();
        }
    }

    @Override
    protected void refresh() {
        mFragment.refresh();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((mFragment instanceof DetailsFragment)
                && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_Y)) {
            ((DetailsFragment)mFragment).showDetails();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mFragment instanceof OnBackPressedListener) {
            if (!((OnBackPressedListener) mFragment).onBackPressed()) {
                super.onBackPressed();
            }
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void showProgress(final boolean show){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEmptyView.setVisibility(View.GONE);
                mContentLoadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void updateEmptyView(final boolean empty) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
        });
    }
    
    public void sort(View v) {
        ((Sortable)mFragment).sort(v);
    }

    public interface OnBackPressedListener {
        /**
         * back has been pressed
         *
         * @return true if the event has been intercepted
         */
        boolean onBackPressed();
    }
}
