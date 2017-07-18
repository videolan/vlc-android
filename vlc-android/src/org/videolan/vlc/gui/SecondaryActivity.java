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
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.StorageBrowserFragment;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.util.MediaLibraryItemComparator;

public class SecondaryActivity extends AudioPlayerContainerActivity {
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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getSupportFragmentManager().getFragments() == null) {
            String fragmentId = getIntent().getStringExtra(KEY_FRAGMENT);
            fetchSecondaryFragment(fragmentId);
            if (mFragment == null){
                finish();
                return;
            }
            getSupportFragmentManager().beginTransaction()
            .add(R.id.fragment_placeholder, mFragment)
            .commit();
            if (VLCApplication.showTvUi() && STORAGE_BROWSER.equals(fragmentId))
                Snackbar.make(getWindow().getDecorView(), R.string.tv_settings_hint, Snackbar.LENGTH_LONG).show();
        }

        if (VLCApplication.showTvUi())
            TvUtil.applyOverscanMargin(this);
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
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mFragment instanceof VideoGridFragment)
            getMenuInflater().inflate(R.menu.video_group, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (menu == null)
            return false;
        Fragment current = mFragment;
        MenuItem item = menu.findItem(R.id.ml_menu_sortby);
        if (item == null)
            return false;
        // Disable the sort option if we can't use it on the current fragment.
        if (current == null || !(current instanceof ISortable)) {
            item.setEnabled(false);
            item.setVisible(false);
        } else {
            ISortable sortable = (ISortable) current;
            item.setEnabled(true);
            item.setVisible(true);
            if (current instanceof VideoGridFragment) {
                menu.findItem(R.id.ml_menu_sortby_length).setVisible(true);
                menu.findItem(R.id.ml_menu_sortby_date).setVisible(true);
                menu.findItem(R.id.ml_menu_sortby_number).setVisible(false);
            } else {
                menu.findItem(R.id.ml_menu_sortby_length).setVisible(false);
                menu.findItem(R.id.ml_menu_sortby_date).setVisible(false);
                menu.findItem(R.id.ml_menu_sortby_number).setVisible(false);
            }
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_TITLE) == 1)
                menu.findItem(R.id.ml_menu_sortby_name).setTitle(R.string.sortby_name_desc);
            else
                menu.findItem(R.id.ml_menu_sortby_name).setTitle(R.string.sortby_name);
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_LENGTH) == 1)
                menu.findItem(R.id.ml_menu_sortby_length).setTitle(R.string.sortby_length_desc);
            else
                menu.findItem(R.id.ml_menu_sortby_length).setTitle(R.string.sortby_length);
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_DATE) == 1)
                menu.findItem(R.id.ml_menu_sortby_date).setTitle(R.string.sortby_date_desc);
            else
                menu.findItem(R.id.ml_menu_sortby_date).setTitle(R.string.sortby_date);
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_NUMBER) == 1)
                menu.findItem(R.id.ml_menu_sortby_number).setTitle(R.string.sortby_number_desc);
            else
                menu.findItem(R.id.ml_menu_sortby_number).setTitle(R.string.sortby_number);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
                ((ISortable) mFragment).sortBy(MediaLibraryItemComparator.SORT_BY_TITLE);
                supportInvalidateOptionsMenu();
                break;
            case R.id.ml_menu_sortby_length:
                ((ISortable) mFragment).sortBy(MediaLibraryItemComparator.SORT_BY_LENGTH);
                supportInvalidateOptionsMenu();
                break;
            case R.id.ml_menu_sortby_date:
                ((ISortable) mFragment).sortBy(MediaLibraryItemComparator.SORT_BY_DATE);
                supportInvalidateOptionsMenu();
                break;
            case R.id.ml_menu_sortby_number:
                ((ISortable) mFragment).sortBy(MediaLibraryItemComparator.SORT_BY_NUMBER);
                supportInvalidateOptionsMenu();
                break;
            case R.id.ml_menu_refresh:
                Medialibrary ml = VLCApplication.getMLInstance();
                if (!ml.isWorking())
                    startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
                break;
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
