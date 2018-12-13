/*
 * *************************************************************************
 *  SecondaryActivity.java
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

package org.videolan.vlc.gui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.appbar.AppBarLayout;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.browser.StorageBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.util.AndroidDevices;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;

public class SecondaryActivity extends ContentActivity {
    public final static String TAG = "VLC/SecondaryActivity";

    public static final int ACTIVITY_RESULT_SECONDARY = 3;

    public static final String KEY_FRAGMENT = "fragment";

    public static final String ALBUMS_SONGS = "albumsSongs";
    public static final String ABOUT = "about";
    public static final String VIDEO_GROUP_LIST = "videoGroupList";
    public static final String STORAGE_BROWSER = "storage_browser";

    Fragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.secondary);
        initAudioPlayerContainerActivity();

        final View fph = findViewById(R.id.fragment_placeholder);
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) fph.getLayoutParams();

        if (AndroidDevices.isTv) {
            TvUtil.INSTANCE.applyOverscanMargin(this);
            params.topMargin = getResources().getDimensionPixelSize(UiTools.getResourceFromAttribute(this, R.attr.actionBarSize));
        } else
            params.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        fph.requestLayout();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder) == null) {
            final String fragmentId = getIntent().getStringExtra(KEY_FRAGMENT);
            fetchSecondaryFragment(fragmentId);
            if (mFragment == null){
                finish();
                return;
            }
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_placeholder, mFragment)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        overridePendingTransition(0,0);
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (isFinishing())
            overridePendingTransition(0, 0);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) MediaParsingServiceKt.reload(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_refresh:
                Medialibrary ml = VLCApplication.getMLInstance();
                if (!ml.isWorking()) MediaParsingServiceKt.rescan(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void fetchSecondaryFragment(String id) {
        switch (id) {
            case ALBUMS_SONGS:
                mFragment = new AudioAlbumsSongsFragment();
                Bundle args = new Bundle();
                args.putParcelable(AudioBrowserFragment.TAG_ITEM, getIntent().getParcelableExtra(AudioBrowserFragment.TAG_ITEM));
                mFragment.setArguments(args);
                break;
            case ABOUT:
                mFragment = new AboutFragment();
                break;
            case VIDEO_GROUP_LIST:
                mFragment = new VideoGridFragment();
                ((VideoGridFragment) mFragment).setGroup(getIntent().getStringExtra("param"));
                break;
            case STORAGE_BROWSER:
                mFragment = new StorageBrowserFragment();
                break;
            default:
                throw new IllegalArgumentException("Wrong fragment id.");
        }
    }
}
