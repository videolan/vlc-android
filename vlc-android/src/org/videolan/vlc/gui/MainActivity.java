/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.internal.NavigationMenuView;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.SimpleArrayMap;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.view.ActionMode;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FilterQueryProvider;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.MediaParsingService;
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
import org.videolan.vlc.gui.browser.FileBrowserFragment;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.browser.NetworkBrowserFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.network.MRLPanelFragment;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.view.HackyDrawerLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.VLCInstance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ContentActivity implements FilterQueryProvider, NavigationView.OnNavigationItemSelectedListener, ExtensionManagerService.ExtensionManagerActivity {
    public final static String TAG = "VLC/MainActivity";

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;
    private static final int ACTIVITY_RESULT_OPEN = 2;
    public static final int ACTIVITY_RESULT_SECONDARY = 3;


    private Medialibrary mMediaLibrary;
    private ExtensionsManager mExtensionsManager;
    private HackyDrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;

    private int mCurrentFragmentId;
    public Fragment mCurrentFragment = null;
    private final SimpleArrayMap<String, WeakReference<Fragment>> mFragmentsStack = new SimpleArrayMap<>();

    private boolean mScanNeeded = false;

    private boolean mFirstRun, mUpgrade;

    // Extensions management
    private ServiceConnection mExtensionServiceConnection;
    private ExtensionManagerService mExtensionManagerService;
    private static final int PLUGIN_NAVIGATION_GROUP = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!VLCInstance.testCompatibleCPU(this)) {
            finish();
            return;
        }

        Permissions.checkReadStoragePermission(this, false);

        /*** Start initializing the UI ***/

        setContentView(R.layout.main);

        mDrawerLayout = (HackyDrawerLayout) findViewById(R.id.root_container);
        setupNavigationView();

        initAudioPlayerContainerActivity();

        if (savedInstanceState != null) {
            FragmentManager fm = getSupportFragmentManager();
            //Restore fragments stack
            if (fm != null && fm.getFragments() != null)
                for (Fragment fragment : fm.getFragments())
                    if (fragment != null) {
                        if (fragment instanceof ExtensionBrowser) {
                            fm.beginTransaction().remove(fragment).commit();
                        } else {
                            mFragmentsStack.put(fragment.getTag(), new WeakReference<>(fragment));
                            fm.beginTransaction().hide(fragment).commit();
                        }
                    }
            mCurrentFragmentId = savedInstanceState.getInt("current", R.id.nav_video);
            if (!currentIdIsExtension()) {
                String tag = getTag(mCurrentFragmentId);
                if (mFragmentsStack.containsKey(tag))
                    mCurrentFragment = mFragmentsStack.get(tag).get();
            }
        } else {
            reloadPreferences();
        }

        /* Set up the action bar */
        prepareActionBar();

        /* Set up the sidebar click listener
         * no need to invalidate menu for now */
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (getCurrentFragment() instanceof MediaBrowserFragment)
                    ((MediaBrowserFragment) getCurrentFragment()).setReadyToDisplay(true);
            }

            // Hack to make navigation drawer browsable with DPAD.
            // see https://code.google.com/p/android/issues/detail?id=190975
            // and http://stackoverflow.com/a/34658002/3485324
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (mNavigationView.requestFocus())
                    ((NavigationMenuView) mNavigationView.getFocusedChild()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        if (getIntent().getBooleanExtra(StartActivity.EXTRA_UPGRADE, false)) {
            mUpgrade = true;
            mFirstRun = getIntent().getBooleanExtra(StartActivity.EXTRA_FIRST_RUN, false);
            /*
             * The sliding menu is automatically opened when the user closes
             * the info dialog. If (for any reason) the dialog is not shown,
             * open the menu after a short delay.
             */
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawerLayout.openDrawer(mNavigationView);
                }
            }, 500);
            getIntent().removeExtra(StartActivity.EXTRA_UPGRADE);
        }

        /* Reload the latest preferences */
        mScanNeeded = savedInstanceState == null && mSettings.getBoolean("auto_rescan", true);
        mExtensionsManager = ExtensionsManager.getInstance();
        mMediaLibrary = VLCApplication.getMLInstance();

    }

    private void setupNavigationView() {
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        if (TextUtils.equals(BuildConfig.FLAVOR_target, "chrome")) {
            MenuItem item = mNavigationView.getMenu().findItem(R.id.nav_directories);
            item.setTitle(R.string.open);
        }

        mNavigationView.getMenu().findItem(R.id.nav_history).setVisible(mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_STORAGE_TAG:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent serviceIntent = new Intent(MediaParsingService.ACTION_INIT, null, this, MediaParsingService.class);
                    serviceIntent.putExtra(StartActivity.EXTRA_FIRST_RUN, mFirstRun);
                    serviceIntent.putExtra(StartActivity.EXTRA_UPGRADE, mUpgrade);
                    startService(serviceIntent);
                } else
                    Permissions.showStoragePermissionDialog(this, false);
                break;
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (BuildConfig.DEBUG)
            createExtensionServiceConnection();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mExtensionServiceConnection != null) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
        }
    }

    private void loadPlugins() {
        Menu navMenu = mNavigationView.getMenu();
        navMenu.removeGroup(PLUGIN_NAVIGATION_GROUP);
        List<ExtensionListing> plugins = mExtensionsManager.getExtensions(getApplication());
        if (plugins.isEmpty()) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
            mExtensionManagerService.stopSelf();
            return;
        }
        PackageManager pm = getPackageManager();
        SubMenu subMenu = navMenu.addSubMenu(PLUGIN_NAVIGATION_GROUP, PLUGIN_NAVIGATION_GROUP,
                PLUGIN_NAVIGATION_GROUP, R.string.plugins);
        for (int i = 0; i < plugins.size(); ++i) {
            ExtensionListing extension = plugins.get(i);
            if (mSettings.getBoolean("extension_" + extension.componentName().getPackageName(), true)) {
                MenuItem item = subMenu.add(PLUGIN_NAVIGATION_GROUP, i, 0, extension.title());
                item.setCheckable(true);
                int iconRes = extension.menuIcon();
                Drawable extensionIcon = null;
                if (iconRes != 0) {
                    try {
                        Resources res = pm.getResourcesForApplication(extension.componentName().getPackageName());
                        extensionIcon = res.getDrawable(extension.menuIcon());
                    } catch (PackageManager.NameNotFoundException e) {}
                }
                if (extensionIcon != null)
                    item.setIcon(extensionIcon);
                else
                    try {
                        item.setIcon(pm.getApplicationIcon(plugins.get(i).componentName().getPackageName()));
                    } catch (PackageManager.NameNotFoundException e) {
                        item.setIcon(R.drawable.icon);
                    }
            }
        }
        if (subMenu.size() == 0)
            navMenu.setGroupVisible(PLUGIN_NAVIGATION_GROUP, false);
        mNavigationView.invalidate();
        onPluginsLoaded();
    }

    private void onPluginsLoaded() {
        if (currentIdIsExtension())
            if (mExtensionsManager.extensionIsEnabled(mSettings, mCurrentFragmentId)) {
                updateCheckedItem(mCurrentFragmentId);
                mExtensionManagerService.openExtension(mCurrentFragmentId);
            }
            else {
                updateCheckedItem(R.id.nav_video);
                showFragment(R.id.nav_video);
            }
        else
            updateCheckedItem(mCurrentFragmentId);
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

    @Override
    protected void onResume() {
        super.onResume();
        if (mMediaLibrary.isInitiated()) {
            /* Load media items from database and storage */
            if (mScanNeeded && Permissions.canReadStorage())
                startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
            else
                restoreCurrentList();
        }
        mNavigationView.setNavigationItemSelectedListener(this);
        mCurrentFragmentId = mSettings.getInt("fragment_id", R.id.nav_video);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        mCurrentFragmentId = mSettings.getInt("fragment_id", R.id.nav_video);
        if (!currentIdIsExtension())
            showFragment(mCurrentFragmentId);
    }

    /**
     * Stop audio player and save opened tab
     */
    @Override
    protected void onPause() {
        super.onPause();
        mNavigationView.setNavigationItemSelectedListener(null);
        if (getChangingConfigurations() == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            mScanNeeded = mMediaLibrary.isWorking();
        }
        /* Save the tab status in pref */
        mSettings.edit().putInt("fragment_id", mCurrentFragmentId).apply();
    }

    protected void onSaveInstanceState(Bundle outState) {
        if (mCurrentFragment instanceof ExtensionBrowser) {
            while (getSupportFragmentManager().popBackStackImmediate())  {}
            if (mCurrentFragment != null)
                getSupportFragmentManager().beginTransaction().remove(mCurrentFragment).commit();
        }
        super.onSaveInstanceState(outState);
        outState.putInt("current", mCurrentFragmentId);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        reloadPreferences();
    }

    @Override
    public void onBackPressed() {
        /* Close the menu first */
        if(mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
            return;
        }

        /* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady() && (mAudioPlayer.clearSearch() || slideDownAudioPlayer()))
            return;

        // If it's the directory view, a "backpressed" action shows a parent.
        Fragment fragment = getCurrentFragment();
        if (fragment instanceof BaseBrowserFragment){
            ((BaseBrowserFragment)fragment).goBack();
            return;
        } else if (fragment instanceof ExtensionBrowser) {
            ((ExtensionBrowser) fragment).goBack();
            return;
        }
        finish();
    }

    @NonNull
    private Fragment getNewFragment(int id) {
        switch (id) {
            case R.id.nav_audio:
                return new AudioBrowserFragment();
            case R.id.nav_directories:
                return new FileBrowserFragment();
            case R.id.nav_history:
                return new HistoryFragment();
            case R.id.nav_network:
                return new NetworkBrowserFragment();
            default:
                return new VideoGridFragment();
        }
    }

    @Override
    public void displayExtensionItems(int extensionId, String title, List<VLCExtensionItem> items, boolean showParams, boolean refresh) {
        if (refresh && getCurrentFragment() instanceof ExtensionBrowser) {
            ExtensionBrowser browser = (ExtensionBrowser) getCurrentFragment();
            browser.doRefresh(title, items);
        } else {
            ExtensionBrowser fragment = new ExtensionBrowser();
            ArrayList<VLCExtensionItem> list = new ArrayList<>(items);
            Bundle args = new Bundle();
            args.putParcelableArrayList(ExtensionBrowser.KEY_ITEMS_LIST, list);
            args.putBoolean(ExtensionBrowser.KEY_SHOW_FAB, showParams);
            args.putString(ExtensionBrowser.KEY_TITLE, title);
            fragment.setArguments(args);
            fragment.setExtensionService(mExtensionManagerService);

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            if (!(mCurrentFragment instanceof ExtensionBrowser)) {
                if (mCurrentFragment != null)
                    ft.hide(mCurrentFragment);
                ft.add(R.id.fragment_placeholder, fragment, title);
                ft.commit();
                mCurrentFragment = fragment;
            } else {
                ft.setCustomAnimations(R.anim.anim_enter_right, 0, R.anim.anim_enter_left, 0);
                ft.replace(R.id.fragment_placeholder, fragment, title);
                ft.addToBackStack(getTag(mCurrentFragmentId));
                ft.commit();
            }
        }
        updateCheckedItem(extensionId);
        mCurrentFragmentId = extensionId;
    }

    /**
     * Show a secondary fragment.
     */
    public void showSecondaryFragment(String fragmentTag) {
        showSecondaryFragment(fragmentTag, null);
    }

    public void showSecondaryFragment(String fragmentTag, String param) {
        Intent i = new Intent(this, SecondaryActivity.class);
        i.putExtra("fragment", fragmentTag);
        if (param != null)
            i.putExtra("param", param);
        startActivityForResult(i, ACTIVITY_RESULT_SECONDARY);
        // Slide down the audio player if needed.
        slideDownAudioPlayer();
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
        mDrawerLayout.closeDrawer(mNavigationView);
        UiTools.setKeyboardVisibility(mDrawerLayout, false);

        // Handle item selection
        switch (item.getItemId()) {
            // Refresh
            case R.id.ml_menu_refresh:
                forceRefresh();
                return true;
            case android.R.id.home:
                // Slide down the audio player.
                if (slideDownAudioPlayer())
                    return true;
                /* Toggle the sidebar */
                return mDrawerToggle.onOptionsItemSelected(item);
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
                startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            switch (resultCode) {
                case PreferencesActivity.RESULT_RESCAN:
                    for (Fragment fragment : getSupportFragmentManager().getFragments())
                        if (fragment instanceof MediaBrowserFragment)
                            ((MediaBrowserFragment) fragment).clear();
                    startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
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
            }
        } else if (requestCode == ACTIVITY_RESULT_OPEN && resultCode == RESULT_OK){
            MediaUtils.openUri(this, data.getData());
        } else if (requestCode == ACTIVITY_RESULT_SECONDARY) {
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
            MenuItemCompat.expandActionView(mMenu.findItem(R.id.ml_menu_filter));
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

    private void reloadPreferences() {
        mCurrentFragmentId = mSettings.getInt("fragment_id", R.id.nav_video);
    }

    @Override
    public Cursor runQuery(final CharSequence constraint) {
        return null;
    }

    //Filtering
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String filterQueryString) {
        if (filterQueryString.length() < 3)
            return false;
        Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).getFilter().filter(filterQueryString);
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // This should not happen
        if(item == null)
            return false;

        getSupportActionBar().setTitle(null); //clear title
        getSupportActionBar().setSubtitle(null); //clear subtitle

        int id = item.getItemId();
        Fragment current = getCurrentFragment();

        if (item.getGroupId() == PLUGIN_NAVIGATION_GROUP)  {
            if(mCurrentFragmentId == id)
                clearBackstackFromClass(ExtensionBrowser.class);
            else
                mExtensionManagerService.openExtension(id);
        } else {
            if (mExtensionServiceConnection != null)
                mExtensionManagerService.disconnect();

            if (current == null) {
                mDrawerLayout.closeDrawer(mNavigationView);
                return false;
            }

            if(mCurrentFragmentId == id) { /* Already selected */
                // Go back at root level of current browser
                if (current instanceof BaseBrowserFragment && !((BaseBrowserFragment) current).isRootDirectory()) {
                    clearBackstackFromClass(current.getClass());
                } else {
                    mDrawerLayout.closeDrawer(mNavigationView);
                    return false;
                }
            }

            switch (id){
                case R.id.nav_about:
                    showSecondaryFragment(SecondaryActivity.ABOUT);
                    break;
                case R.id.nav_settings:
                    startActivityForResult(new Intent(this, PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
                    break;
                case R.id.nav_mrl:
                    new MRLPanelFragment().show(getSupportFragmentManager(), "fragment_mrl");
                    break;
                case R.id.nav_directories:
                    if (TextUtils.equals(BuildConfig.FLAVOR_target, "chrome")) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("audio/* video/*");
                        startActivityForResult(intent, ACTIVITY_RESULT_OPEN);
                        mDrawerLayout.closeDrawer(mNavigationView);
                        return true;
                    }
                default:
                /* Slide down the audio player */
                    slideDownAudioPlayer();
                /* Switch the fragment */
                    updateCheckedItem(id);
                    showFragment(id);
            }
        }
        mDrawerLayout.closeDrawer(mNavigationView);
        return true;
    }

    public void updateCheckedItem(int id) {
        if (mNavigationView.getMenu().findItem(mCurrentFragmentId) != null) {
            mNavigationView.getMenu().findItem(mCurrentFragmentId).setChecked(false);
            mNavigationView.getMenu().findItem(id).setChecked(true);
        }
    }

    public void showFragment(int id) {
        FragmentManager fm = getSupportFragmentManager();
        //noinspection StatementWithEmptyBody
        while (fm.popBackStackImmediate()); // Clear backstack
        String tag = getTag(id);
        //Get new fragment
        Fragment fragment = null;
        boolean add = false;
        WeakReference<Fragment> wr = mFragmentsStack.get(tag);
        if (wr != null)
            fragment = wr.get();
        if (fragment == null) {
            fragment = getNewFragment(id);
            mFragmentsStack.put(tag, new WeakReference<>(fragment));
            add = true;
        }
        if (mCurrentFragment != null)
            if (mCurrentFragment instanceof ExtensionBrowser)
                fm.beginTransaction().remove(mCurrentFragment).commit();
            else
                fm.beginTransaction().hide(mCurrentFragment).commit();
        FragmentTransaction ft = fm.beginTransaction();
        if (add)
            ft.add(R.id.fragment_placeholder, fragment, tag);
        else
            ft.show(fragment);
        ft.commit();
        mCurrentFragment = fragment;
        mCurrentFragmentId = id;
    }

    private void clearBackstackFromClass(Class clazz) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = getCurrentFragment();
        while (clazz.isInstance(current)) {
            if (!fm.popBackStackImmediate())
                break;
            current = getCurrentFragment();
        }
    }

    private String getTag(int id){
        switch (id){
            case R.id.nav_about:
                return ID_ABOUT;
            case R.id.nav_settings:
                return ID_PREFERENCES;
            case R.id.nav_audio:
                return ID_AUDIO;
            case R.id.nav_directories:
                return ID_DIRECTORIES;
            case R.id.nav_history:
                return ID_HISTORY;
            case R.id.nav_mrl:
                return ID_MRL;
            case R.id.nav_network:
                return ID_NETWORK;
            default:
                return ID_VIDEO;
        }
    }

    protected Fragment getCurrentFragment() {
        return mCurrentFragment instanceof BaseBrowserFragment || currentIdIsExtension()
                ? getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder)
                : mCurrentFragment;
    }

    public boolean currentIdIsExtension() {
        return mCurrentFragmentId <= 100;
    }


}
