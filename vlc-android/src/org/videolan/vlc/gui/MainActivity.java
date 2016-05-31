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
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenuView;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FilterQueryProvider;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.browser.BaseBrowserFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.gui.browser.FileBrowserFragment;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.browser.NetworkBrowserFragment;
import org.videolan.vlc.gui.helpers.SearchSuggestionsAdapter;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.network.MRLPanelFragment;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.gui.view.HackyDrawerLayout;
import org.videolan.vlc.interfaces.IHistory;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AudioPlayerContainerActivity implements FilterQueryProvider, NavigationView.OnNavigationItemSelectedListener, ExtensionManagerService.ExtensionManagerActivity {
    public final static String TAG = "VLC/MainActivity";

    private static final String PREF_FIRST_RUN = "first_run";

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;
    private static final int ACTIVITY_RESULT_OPEN = 2;
    public static final int ACTIVITY_RESULT_SECONDARY = 3;
    private static final int ACTIVITY_SHOW_INFOLAYOUT = 2;
    private static final int ACTIVITY_SHOW_PROGRESSBAR = 3;
    private static final int ACTIVITY_HIDE_PROGRESSBAR = 4;
    private static final int ACTIVITY_SHOW_TEXTINFO = 5;


    MediaLibrary mMediaLibrary;

    private HackyDrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;

    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;
    private int mCurrentFragmentId;


    private int mVersionNumber = -1;
    private boolean mFirstRun = false;
    private boolean mScanNeeded = false;

    private Handler mHandler = new MainActivityHandler(this);
    private int mActionBarIconId = -1;
    Menu mMenu;
    private SearchView mSearchView;

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
        /* Enable the indeterminate progress feature */
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        /* Get the current version from package */
        mVersionNumber = BuildConfig.VERSION_CODE;

        /* Check if it's the first run */
        mFirstRun = mSettings.getInt(PREF_FIRST_RUN, -1) != mVersionNumber;
        if (mFirstRun) {
            Editor editor = mSettings.edit();
            editor.putInt(PREF_FIRST_RUN, mVersionNumber);
            Util.commitPreferences(editor);
        }

        Permissions.checkReadStoragePermission(this, false);

        mMediaLibrary = MediaLibrary.getInstance();
        if (mMediaLibrary.getMediaItems().isEmpty()) {
            if (mSettings.getBoolean(PreferencesActivity.AUTO_RESCAN, true))
                mMediaLibrary.scanMediaItems();
            else
                mMediaLibrary.loadMediaItems();
        }

        /*** Start initializing the UI ***/

        setContentView(R.layout.main);

        mDrawerLayout = (HackyDrawerLayout) findViewById(R.id.root_container);
        setupNavigationView();

        initAudioPlayerContainerActivity();

        if (savedInstanceState != null){
            mCurrentFragmentId = savedInstanceState.getInt("current");
            if (mCurrentFragmentId > 0)
                mNavigationView.setCheckedItem(mCurrentFragmentId);
        }


        /* Initialize UI variables */
        mInfoLayout = findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) findViewById(R.id.info_progress);
        mInfoText = (TextView) findViewById(R.id.info_text);

        /* Set up the action bar */
        prepareActionBar();

        /* Set up the sidebar click listener
         * no need to invalidate menu for now */
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close){
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder) instanceof MediaBrowserFragment)
                    ((MediaBrowserFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder)).setReadyToDisplay(true);
            }

            // Hack to make navigation drawer browsable with DPAD.
            // see https://code.google.com/p/android/issues/detail?id=190975
            // and http://stackoverflow.com/a/34658002/3485324
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if(mNavigationView.requestFocus())
                    ((NavigationMenuView)mNavigationView.getFocusedChild()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        if (mFirstRun) {
            /*
             * The sliding menu is automatically opened when the user closes
             * the info dialog. If (for any reason) the dialog is not shown,
             * open the menu after a short delay.
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawerLayout.openDrawer(mNavigationView);
                }
            }, 500);
        }

        /* Reload the latest preferences */
        reloadPreferences();
    }

    private void setupNavigationView() {
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        if (TextUtils.equals(BuildConfig.FLAVOR_target, "chrome")) {
            MenuItem item = mNavigationView.getMenu().findItem(R.id.nav_directories);
            item.setTitle(R.string.open);
        }

        mNavigationView.getMenu().findItem(R.id.nav_history).setVisible(mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true));


        if (AndroidUtil.isLolliPopOrLater())
            mNavigationView.setPadding(0, mNavigationView.getPaddingTop()/2, 0, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_STORAGE_TAG:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    forceRefresh(getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder));
                else
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
        setActionBarFocus();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

          //Deactivated for now
//        createExtensionServiceConnection();

        mNavigationView.setNavigationItemSelectedListener(this);
        clearBackstackFromClass(ExtensionBrowser.class);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNavigationView.setNavigationItemSelectedListener(null);
        if (mExtensionServiceConnection != null) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
        }
    }

    private void loadPlugins() {
        Menu navMenu = mNavigationView.getMenu();
        navMenu.removeGroup(PLUGIN_NAVIGATION_GROUP);
        List<ExtensionListing> plugins = mExtensionManagerService.updateAvailableExtensions();
        if (plugins.isEmpty()) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
            mExtensionManagerService.stopSelf();
            return;
        }
        PackageManager pm = getPackageManager();
            SubMenu subMenu = navMenu.addSubMenu(PLUGIN_NAVIGATION_GROUP, PLUGIN_NAVIGATION_GROUP,
               PLUGIN_NAVIGATION_GROUP, R.string.plugins);
        for (int i = 0 ; i < plugins.size() ; ++i) {
            ExtensionListing extension = plugins.get(i);
            MenuItem item = subMenu.add(PLUGIN_NAVIGATION_GROUP, i, 0, extension.title());
            int iconRes = extension.menuIcon();
            Drawable extensionIcon = null;
            if (iconRes != 0) {
                try {
                    Resources res = VLCApplication.getAppContext().getPackageManager().getResourcesForApplication(extension.componentName().getPackageName());
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
        mNavigationView.invalidate();
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

        /* Load media items from database and storage */
        if (mScanNeeded)
            mMediaLibrary.scanMediaItems();
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED)
            mActionBar.hide();
        mNavigationView.setCheckedItem(mCurrentFragmentId);
        mCurrentFragmentId = mSettings.getInt("fragment_id", R.id.nav_video);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        // Figure out if currently-loaded fragment is a top-level fragment.
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);

        /**
         * Restore the last view.
         *
         * Replace:
         * - null fragments (freshly opened Activity)
         * - Wrong fragment open AND currently displayed fragment is a top-level fragment
         *
         * Do not replace:
         * - Non-sidebar fragments.
         * It will try to remove() the currently displayed fragment
         * (i.e. tracks) and replace it with a blank screen. (stuck menu bug)
         */
        if (current == null) {
            mNavigationView.setCheckedItem(mCurrentFragmentId);
            Fragment ff = getFragment(mCurrentFragmentId);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_placeholder, ff, getTag(mCurrentFragmentId));
            ft.commit();
        }
    }

    /**
     * Stop audio player and save opened tab
     */
    @Override
    protected void onPause() {
        super.onPause();
        /* Check for an ongoing scan that needs to be resumed during onResume */
        mScanNeeded = mMediaLibrary.isWorking();
        /* Stop scanning for files */
        mMediaLibrary.stop();
        /* Save the tab status in pref */
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt("fragment_id", mCurrentFragmentId);
        Util.commitPreferences(editor);
    }

    protected void onSaveInstanceState(Bundle outState) {
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
        if (mAudioPlayer.clearSearch() || slideDownAudioPlayer())
            return;

        // If it's the directory view, a "backpressed" action shows a parent.
        Fragment fragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_placeholder);
        if (fragment instanceof BaseBrowserFragment){
            ((BaseBrowserFragment)fragment).goBack();
            return;
        } else if (fragment instanceof ExtensionBrowser) {
            ((ExtensionBrowser) fragment).goBack();
            return;
        }
        finish();
    }

    private Fragment getFragment(int id)
    {
        Fragment frag = getSupportFragmentManager().findFragmentByTag(getTag(id));
        if (frag != null)
            return frag;
        switch (id) {
            case R.id.nav_audio:
                return new AudioBrowserFragment();
            case R.id.nav_directories:
                return new FileBrowserFragment();
            case R.id.nav_history:
                return new HistoryFragment();
            case R.id.nav_mrl:
                return new MRLPanelFragment();
            case R.id.nav_network:
                return new NetworkBrowserFragment();
            default:
                return new VideoGridFragment();
        }
    }

    @Override
    public void displayExtensionItems(String title, List<VLCExtensionItem> items, boolean showParams, boolean refresh) {
        FragmentManager fm = getSupportFragmentManager();

        if (refresh && fm.findFragmentById(R.id.fragment_placeholder) instanceof ExtensionBrowser) {
            ExtensionBrowser browser = (ExtensionBrowser) fm.findFragmentById(R.id.fragment_placeholder);
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

            FragmentTransaction ft = fm.beginTransaction();
            ft.setCustomAnimations(R.anim.anim_enter_right, 0, R.anim.anim_enter_left, 0);
            ft.replace(R.id.fragment_placeholder, fragment, title);
            if (!(fm.findFragmentById(R.id.fragment_placeholder) instanceof ExtensionBrowser))
                ft.addToBackStack(getTag(mCurrentFragmentId));
            else
                ft.addToBackStack(title);
            ft.commit();
        }
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

    /** Create menu from XML
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        /* Note: on Android 3.0+ with an action bar this method
         * is called while the view is created. This can happen
         * any time after onCreate.
         */
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.media_library, menu);

        if (AndroidUtil.isFroyoOrLater()) {
            SearchManager searchManager =
                    (SearchManager) VLCApplication.getAppContext().getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.ml_menu_search));
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchView.setQueryHint(getString(R.string.search_hint));
            SearchSuggestionsAdapter searchSuggestionsAdapter = new SearchSuggestionsAdapter(this, null);
            searchSuggestionsAdapter.setFilterQueryProvider(this);
            mSearchView.setSuggestionsAdapter(searchSuggestionsAdapter);
        } else
            menu.findItem(R.id.ml_menu_search).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (menu == null)
            return false;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
        MenuItem item;
        // Disable the sort option if we can't use it on the current fragment.
        if (current == null || !(current instanceof ISortable)) {
            item = menu.findItem(R.id.ml_menu_sortby);
            if (item == null)
                return false;
            item.setEnabled(false);
            item.setVisible(false);
        } else {
            ISortable sortable = (ISortable) current;
            item = menu.findItem(R.id.ml_menu_sortby);
            if (item == null)
                return false;
            item.setEnabled(true);
            item.setVisible(true);
            item = menu.findItem(R.id.ml_menu_sortby_name);
            if (sortable.sortDirection(VideoListAdapter.SORT_BY_TITLE) == 1)
                item.setTitle(R.string.sortby_name_desc);
            else
                item.setTitle(R.string.sortby_name);
            item = menu.findItem(R.id.ml_menu_sortby_length);
            if (sortable.sortDirection(VideoListAdapter.SORT_BY_LENGTH) == 1)
                item.setTitle(R.string.sortby_length_desc);
            else
                item.setTitle(R.string.sortby_length);
            item = menu.findItem(R.id.ml_menu_sortby_date);
            if (sortable.sortDirection(VideoListAdapter.SORT_BY_DATE) == 1)
                item.setTitle(R.string.sortby_date_desc);
            else
                item.setTitle(R.string.sortby_date);
        }

        if (current instanceof NetworkBrowserFragment &&
                !((NetworkBrowserFragment)current).isRootDirectory()) {
            MenuItemCompat.setShowAsAction(menu.findItem(R.id.ml_menu_search), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            item = menu.findItem(R.id.ml_menu_save);
            item.setVisible(true);
            String mrl = ((BaseBrowserFragment)current).mMrl;
            boolean isFavorite = MediaDatabase.getInstance().networkFavExists(Uri.parse(mrl));
            item.setIcon(isFavorite ?
                    R.drawable.ic_menu_bookmark_w :
                    R.drawable.ic_menu_bookmark_outline_w);
            item.setTitle(isFavorite ? R.string.favorites_remove : R.string.favorites_add);
        } else
            menu.findItem(R.id.ml_menu_save).setVisible(false);
        if (current instanceof IHistory)
            menu.findItem(R.id.ml_menu_clean).setVisible(!((IHistory) current).isEmpty());
        boolean showLast = current instanceof AudioBrowserFragment || current instanceof VideoGridFragment;
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(showLast);
        return true;
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        UiTools.setKeyboardVisibility(mDrawerLayout, false);

        // Current fragment loaded
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
            case R.id.ml_menu_sortby_date:
                if (current == null)
                    break;
                if (current instanceof ISortable) {
                    int sortBy = VideoListAdapter.SORT_BY_TITLE;
                    if (item.getItemId() == R.id.ml_menu_sortby_length)
                        sortBy = VideoListAdapter.SORT_BY_LENGTH;
                    else if(item.getItemId() == R.id.ml_menu_sortby_date)
                        sortBy = VideoListAdapter.SORT_BY_DATE;
                    ((ISortable) current).sortBy(sortBy);
                    supportInvalidateOptionsMenu();
                }
                break;
            case R.id.ml_menu_equalizer:
                showSecondaryFragment(SecondaryActivity.EQUALIZER);
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                forceRefresh(current);
                break;
            // Restore last playlist
            case R.id.ml_menu_last_playlist:
                boolean audio = current instanceof AudioBrowserFragment;
                    Intent i = new Intent(audio ? PlaybackService.ACTION_REMOTE_LAST_PLAYLIST :
                           PlaybackService.ACTION_REMOTE_LAST_VIDEO_PLAYLIST);
                    sendBroadcast(i);
                break;
            case android.R.id.home:
                // Slide down the audio player.
                if (slideDownAudioPlayer())
                    break;
                /* Toggle the sidebar */
                if (mDrawerToggle.onOptionsItemSelected(item)) {
                    return true;
                }
                break;
            case R.id.ml_menu_clean:
                if (current instanceof IHistory)
                    ((IHistory)current).clearHistory();
                break;
            case R.id.ml_menu_save:
                if (current == null)
                    break;
                ((NetworkBrowserFragment)current).toggleFavorite();
                item.setIcon(R.drawable.ic_menu_bookmark_w);
                break;
        }
        mDrawerLayout.closeDrawer(mNavigationView);
        return super.onOptionsItemSelected(item);
    }

    public void forceRefresh() {
        forceRefresh(getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder));
    }

    private void forceRefresh(Fragment current) {
        if (!mMediaLibrary.isWorking()) {
            if(current != null && current instanceof IRefreshable)
                ((IRefreshable) current).refresh();
            else
                mMediaLibrary.scanMediaItems(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                for (Fragment fragment : getSupportFragmentManager().getFragments())
                    if (fragment instanceof MediaBrowserFragment)
                        ((MediaBrowserFragment) fragment).clear();
                mMediaLibrary.scanMediaItems(true);
            } else if (resultCode == PreferencesActivity.RESULT_RESTART) {
                Intent intent = new Intent(MainActivity.this, StartActivity.class);
                finish();
                startActivity(intent);
            }
        } else if (requestCode == ACTIVITY_RESULT_OPEN && resultCode == RESULT_OK){
            MediaUtils.openUri(this, data.getData());
        } else if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                forceRefresh(getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder));
            }
        }
    }

    private void setActionBarFocus() {
        View v = ((ViewGroup)findViewById(R.id.main_toolbar)).getChildAt(0);
        if (v == null || ! (v instanceof ImageButton))
            return;
        if ((mActionBarIconId == -1) &&
                (v.getId() == -1)  &&
                (v.getNextFocusDownId() == -1) &&
                (v.getNextFocusUpId() == -1) &&
                (v.getNextFocusLeftId() == -1) &&
                (v.getNextFocusRightId() == -1)) {
            mActionBarIconId = UiTools.generateViewId();
            v.setId(mActionBarIconId);
            v.setNextFocusUpId(mActionBarIconId);
            v.setNextFocusDownId(mActionBarIconId);
            v.setNextFocusLeftId(mActionBarIconId);
            v.setNextFocusRightId(R.id.ml_menu_search);
            if (AndroidUtil.isHoneycombOrLater())
                v.setNextFocusForwardId(mActionBarIconId);
            if (findViewById(R.id.ml_menu_search) != null)
                findViewById(R.id.ml_menu_search).setNextFocusLeftId(mActionBarIconId);
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
    public Cursor runQuery(CharSequence constraint) {
        return MediaDatabase.getInstance().queryMedia(constraint.toString());
    }

    private static class MainActivityHandler extends WeakHandler<MainActivity> {
        public MainActivityHandler(MainActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity ma = getOwner();
            if(ma == null) return;

            switch (msg.what) {
                case ACTIVITY_SHOW_INFOLAYOUT:
                    ma.mInfoLayout.setVisibility(View.VISIBLE);
                    break;
                case ACTIVITY_SHOW_PROGRESSBAR:
                    ma.mInfoProgress.setVisibility(View.VISIBLE);
                    ma.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                case ACTIVITY_HIDE_PROGRESSBAR:
                    ma.mInfoProgress.setVisibility(View.GONE);
                    ma.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                case ACTIVITY_SHOW_TEXTINFO:
                    String info = (String) msg.obj;
                    int max = msg.arg1;
                    int progress = msg.arg2;
                    ma.mInfoText.setText(info);
                    ma.mInfoProgress.setMax(max);
                    ma.mInfoProgress.setProgress(progress);

                    if (info == null) {
                    /* Cancel any upcoming visibility change */
                        removeMessages(ACTIVITY_SHOW_INFOLAYOUT);
                        ma.mInfoLayout.setVisibility(View.GONE);
                    } else {
                    /* Slightly delay the appearance of the progress bar to avoid unnecessary flickering */
                        if (!hasMessages(ACTIVITY_SHOW_INFOLAYOUT))
                            sendEmptyMessageDelayed(ACTIVITY_SHOW_INFOLAYOUT, 300);
                    }
                    break;
            }
        }
    }

    public void showProgressBar() {
        mHandler.obtainMessage(ACTIVITY_SHOW_PROGRESSBAR).sendToTarget();
    }

    public void hideProgressBar() {
        mHandler.obtainMessage(ACTIVITY_HIDE_PROGRESSBAR).sendToTarget();
    }

    public void sendTextInfo(String info, int progress, int max) {
        mHandler.obtainMessage(ACTIVITY_SHOW_TEXTINFO, max, progress, info).sendToTarget();
    }

    public void clearTextInfo() {
        mHandler.obtainMessage(ACTIVITY_SHOW_TEXTINFO, 0, 100, null).sendToTarget();
    }

    protected void onPanelClosedUiSet() {
        mDrawerLayout.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    protected void onPanelOpenedEntirelyUiSet() {
        mDrawerLayout.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_UNLOCKED);
    }

    protected void onPanelOpenedUiSet() {
        mDrawerLayout.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_UNLOCKED);
        removeTipViewIfDisplayed();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // This should not happen
        if(item == null)
            return false;

        getSupportActionBar().setTitle(null); //clear title
        getSupportActionBar().setSubtitle(null); //clear subtitle

        int id = item.getItemId();
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);

        if (item.getGroupId() == PLUGIN_NAVIGATION_GROUP)  {
            mExtensionManagerService.openExtension(id);
            mCurrentFragmentId = id;
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

            String tag = getTag(id);
            switch (id){
                case R.id.nav_about:
                    showSecondaryFragment(SecondaryActivity.ABOUT);
                    break;
                case R.id.nav_settings:
                    startActivityForResult(new Intent(this, PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
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
                    Fragment fragment = getFragment(id);
                    if (fragment instanceof MediaBrowserFragment)
                        ((MediaBrowserFragment)fragment).setReadyToDisplay(false);
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.replace(R.id.fragment_placeholder, fragment, tag);
                    ft.addToBackStack(getTag(mCurrentFragmentId));
                    ft.commit();
                    mCurrentFragmentId = id;
            }
        }
        mNavigationView.setCheckedItem(mCurrentFragmentId);
        mDrawerLayout.closeDrawer(mNavigationView);
        return true;
    }

    private void clearBackstackFromClass(Class clazz) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);
        while (clazz.isInstance(current)) {
            if (!fm.popBackStackImmediate())
                break;
            current = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_placeholder);
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
}
