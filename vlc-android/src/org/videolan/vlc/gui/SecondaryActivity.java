/*
 * *************************************************************************
 *  SecondaryActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.video.MediaInfoFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;

import java.util.ArrayList;
import java.util.List;

public class SecondaryActivity  extends ActionBarActivity {
    public final static String TAG = "VLC/EqualizerFragment";

    Fragment mFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Theme must be applied before super.onCreate */
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("enable_black_theme", false)) {
            setTheme(R.style.Theme_VLC_Black);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.secondary);

        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        String fragmentId = getIntent().getStringExtra("fragment");
        fetchSecondaryFragment(fragmentId);
        if (mFragment == null){
            finish();
            return;
        }

        if (getSupportFragmentManager().getFragments() == null) {
            getSupportFragmentManager().beginTransaction()
            .add(R.id.fragment_placeholder, mFragment)
            .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mFragment instanceof VideoGridFragment)
            getMenuInflater().inflate(R.menu.video_group, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                ((ISortable) mFragment).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
                ? VideoListAdapter.SORT_BY_TITLE
                : VideoListAdapter.SORT_BY_LENGTH);
                break;
            case R.id.ml_menu_refresh:
                if (!MediaLibrary.getInstance().isWorking())
                    MediaLibrary.getInstance().loadMediaItems(this, true);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void fetchSecondaryFragment(String id) {
        if (id.equals("albumsSongs")) {
            ArrayList<MediaWrapper> mediaList = getIntent().getParcelableArrayListExtra("list");
            String filter = getIntent().getStringExtra("filter");
            mFragment = new AudioAlbumsSongsFragment();
            ((AudioAlbumsSongsFragment)mFragment).setMediaList(mediaList, filter);
        } else if(id.equals("equalizer")) {
            mFragment = new EqualizerFragment();
        } else if(id.equals("about")) {
            mFragment = new AboutFragment();
        } else if(id.equals("mediaInfo")) {
            mFragment = new MediaInfoFragment();
            ((MediaInfoFragment)mFragment).setMediaLocation(getIntent().getStringExtra("param"));
        } else if(id.equals("videoGroupList")) {
            mFragment = new VideoGridFragment();
            ((VideoGridFragment)mFragment).setGroup(getIntent().getStringExtra("param"));
        }
        else {
            throw new IllegalArgumentException("Wrong fragment id.");
        }
    }
}
