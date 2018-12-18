/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2018 VLC authors and VideoLAN
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

package org.videolan.vlc.gui;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.MediaParsingServiceKt;
import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.browser.BaseBrowserFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.gui.helpers.Navigator;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.view.HackyDrawerLayout;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Util;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends ContentActivity implements ExtensionManagerService.ExtensionManagerActivity {
    public final static String TAG = "VLC/MainActivity";

    private Medialibrary mMediaLibrary;
    private ExtensionsManager mExtensionsManager;
    private HackyDrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private Navigator mNavigator;

    private boolean mScanNeeded = false;

    // Extensions management
    private ServiceConnection mExtensionServiceConnection;
    private ExtensionManagerService mExtensionManagerService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.checkCpuCompatibility(this);
        Permissions.checkReadStoragePermission(this, false);
        /*** Start initializing the UI ***/
        setContentView(R.layout.main);
        mDrawerLayout = findViewById(R.id.root_container);
        setupNavigationView();
        initAudioPlayerContainerActivity();
        mNavigator = new Navigator(this, mSettings, mExtensionManagerService, savedInstanceState, getIntent().getIntExtra(Constants.EXTRA_TARGET, 0));
        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra(Constants.EXTRA_UPGRADE, false)) {
                /*
                 * The sliding menu is automatically opened when the user closes
                 * the info dialog. If (for any reason) the dialog is not shown,
                 * open the menu after a short delay.
                 */
                mActivityHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mNavigationView);
                    }
                }, 500);
            }
        }

        /* Set up the action bar */
        prepareActionBar();

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        /* Reload the latest preferences */
        mScanNeeded = savedInstanceState == null && mSettings.getBoolean("auto_rescan", true);
        mExtensionsManager = ExtensionsManager.getInstance();
        mMediaLibrary = VLCApplication.getMLInstance();
    }

    private void setupNavigationView() {
        mNavigationView = findViewById(R.id.navigation);
        mNavigationView.getMenu().findItem(R.id.nav_history).setVisible(mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mMediaLibrary.isInitiated()) {
            /* Load media items from database and storage */
            if (mScanNeeded && Permissions.canReadStorage(this)) MediaParsingServiceKt.reload(this);
        }
        if (BuildConfig.DEBUG) createExtensionServiceConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavMenu();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNavigationView.setNavigationItemSelectedListener(null);
        if (getChangingConfigurations() == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            mScanNeeded = mMediaLibrary.isWorking();
        }
        if (isExtensionServiceBinded()) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
        }
        if (mNavigator.currentIdIsExtension())
            mSettings.edit()
                    .putString("current_extension_name", mExtensionsManager.getExtensions(getApplication(), false).get(mNavigator.getCurrentFragmentId()).componentName().getPackageName())
                    .apply();
    }

    private void updateNavMenu() {
        mNavigationView.setNavigationItemSelectedListener(mNavigator);
        final boolean wasVisible = mNavigationView.getMenu().findItem(R.id.nav_video).isVisible();
        final boolean scan = mSettings.getInt(Constants.KEY_MEDIALIBRARY_SCAN, Constants.ML_SCAN_OFF) == Constants.ML_SCAN_ON;
        if (wasVisible != scan) mActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                mNavigationView.getMenu().findItem(R.id.nav_audio).setVisible(scan);
                mNavigationView.getMenu().findItem(R.id.nav_video).setVisible(scan);
                if (scan) getNavigator().showFragment(R.id.nav_video);
            }
        });
    }

    public boolean isExtensionServiceBinded() {
        return mExtensionServiceConnection != null;
    }

    private void loadPlugins() {
        final List<ExtensionListing> plugins = mExtensionsManager.getExtensions(this, true);
        if (plugins.isEmpty()) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
            mExtensionManagerService.stopSelf();
            return;
        }
        final MenuItem extensionGroup = mNavigationView.getMenu().findItem(R.id.extensions_group);
        extensionGroup.getSubMenu().clear();
        for (int id = 0; id < plugins.size(); ++id) {
            final ExtensionListing extension = plugins.get(id);
            final String key = "extension_" + extension.componentName().getPackageName();
            if (mSettings.contains(key)) {
                mExtensionsManager.displayPlugin(this, id, extension, mSettings.getBoolean(key, false));
            } else {
                mExtensionsManager.showExtensionPermissionDialog(this, id, extension, key);
            }
        }
        if (extensionGroup.getSubMenu().size() == 0) extensionGroup.setVisible(false);
        onPluginsLoaded();
        mNavigationView.invalidate();
    }

    private void onPluginsLoaded() {
        if (mNavigator.getCurrentFragment() == null && mNavigator.currentIdIsExtension())
            if (mExtensionsManager.previousExtensionIsEnabled(getApplication()))
                mExtensionManagerService.openExtension(mNavigator.getCurrentFragmentId());
            else
                mNavigator.showFragment(R.id.nav_video);
    }

    private void createExtensionServiceConnection() {
        mExtensionServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mExtensionManagerService = ((ExtensionManagerService.LocalBinder)service).getService();
                mExtensionManagerService.setExtensionManagerActivity(MainActivity.this);
                loadPlugins();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        // Bind service which discoverves au connects toplugins
        if (!bindService(new Intent(MainActivity.this,
                ExtensionManagerService.class), mExtensionServiceConnection, Context.BIND_AUTO_CREATE))
            mExtensionServiceConnection = null;
    }

    protected void onSaveInstanceState(Bundle outState) {
        final Fragment current = mNavigator.getCurrentFragment();
        if (!(current instanceof ExtensionBrowser)) getSupportFragmentManager().putFragment(outState, "current_fragment", current);
        super.onSaveInstanceState(outState);
        outState.putInt("current", mNavigator.getCurrentFragmentId());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        mNavigator.reloadPreferences();
    }

    @Override
    public void onBackPressed() {
        /* Close the menu first */
        if (mDrawerLayout.isDrawerOpen(mNavigationView)) {
            closeDrawer();
            return;
        }

        /* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady() && (mAudioPlayer.backPressed() || slideDownAudioPlayer()))
            return;

        // If it's the directory view, a "backpressed" action shows a parent.
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof BaseBrowserFragment && ((BaseBrowserFragment)fragment).goBack()){
            return;
        } else if (fragment instanceof ExtensionBrowser) {
            ((ExtensionBrowser) fragment).goBack();
            return;
        }
        if (AndroidUtil.isNougatOrLater && isInMultiWindowMode()) {
            UiTools.confirmExit(this);
            return;
        }
        finish();
    }

    @Override
    public void displayExtensionItems(int extensionId, String title, List<VLCExtensionItem> items, boolean showParams, boolean refresh) {
        mNavigator.displayExtensionItems(extensionId, title, items, showParams, refresh);
        mNavigationView.getMenu().findItem(extensionId).setCheckable(true);
        updateCheckedItem(extensionId);
    }

    @Nullable
    @Override
    public ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback) {
        mAppBarLayout.setExpanded(true);
        return super.startSupportActionMode(callback);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        closeDrawer();
        UiTools.setKeyboardVisibility(mDrawerLayout, false);

        // Handle item selection
        switch (item.getItemId()) {
            // Refresh
            case R.id.ml_menu_refresh:
                forceRefresh();
                return true;
            case android.R.id.home:
                // Slide down the audio player or toggle the sidebar
                return slideDownAudioPlayer() || mDrawerToggle.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void forceRefresh() {
        forceRefresh(getCurrentFragment());
    }

    private void forceRefresh(Fragment current) {
        if (!mMediaLibrary.isWorking()) {
            if(current != null && current instanceof IRefreshable)
                ((IRefreshable) current).refresh();
            else
                MediaParsingServiceKt.rescan(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.ACTIVITY_RESULT_PREFERENCES) {
            switch (resultCode) {
                case PreferencesActivity.RESULT_RESCAN:
                    MediaParsingServiceKt.reload(this);
                    break;
                case PreferencesActivity.RESULT_RESTART:
                case PreferencesActivity.RESULT_RESTART_APP:
                    Intent intent = new Intent(MainActivity.this, resultCode == PreferencesActivity.RESULT_RESTART_APP ? StartActivity.class : MainActivity.class);
                    finish();
                    startActivity(intent);
                    break;
                case PreferencesActivity.RESULT_UPDATE_SEEN_MEDIA:
                    for (Fragment fragment : getSupportFragmentManager().getFragments())
                        if (fragment instanceof VideoGridFragment)
                            ((VideoGridFragment) fragment).updateSeenMediaMarker();
                    break;
                case PreferencesActivity.RESULT_UPDATE_ARTISTS:
                    final Fragment fragment = getCurrentFragment();
                    if (fragment instanceof AudioBrowserFragment) ((AudioBrowserFragment) fragment).updateArtists();
            }
        } else if (requestCode == Constants.ACTIVITY_RESULT_OPEN && resultCode == RESULT_OK){
            MediaUtils.INSTANCE.openUri(this, data.getData());
        } else if (requestCode == Constants.ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                forceRefresh(getCurrentFragment());
            }
        }
    }

    // Note. onKeyDown will not occur while moving within a list
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if ((keyCode == KeyEvent.KEYCODE_MENU) &&
                (Build.VERSION.SDK_INT <= 16) &&
                (Build.MANUFACTURER.compareTo("LGE") == 0)) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            mToolbar.getMenu().findItem(R.id.ml_menu_filter).expandActionView();
        }
        return super.onKeyDown(keyCode, event);
    }

    // Note. onKeyDown will not occur while moving within a list
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if ((keyCode == KeyEvent.KEYCODE_MENU) &&
                (Build.VERSION.SDK_INT <= 16) &&
                (Build.MANUFACTURER.compareTo("LGE") == 0)) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void closeDrawer() {
        mDrawerLayout.closeDrawer(mNavigationView);
    }

    public void updateCheckedItem(int id) {
        switch (id) {
            case R.id.nav_mrl:
            case R.id.nav_settings:
            case R.id.nav_about:
                return;
            default:
                final int currentId = mNavigator.getCurrentFragmentId();
                final MenuItem target = mNavigationView.getMenu().findItem(id);
                if (id != currentId && target != null) {
                    final MenuItem current = mNavigationView.getMenu().findItem(currentId);
                    if (current != null) current.setChecked(false);
                    target.setChecked(true);
                    /* Save the tab status in pref */
                    mSettings.edit().putInt("fragment_id", id).apply();
                }
        }
    }

    public Navigator getNavigator() {
        return mNavigator;
    }
}
