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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.audio.AudioService;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.SidebarAdapter.SidebarEntry;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.gui.network.NetworkFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.HackyDrawerLayout;
import org.videolan.vlc.widget.SlidingPaneLayout;

public class MainActivity extends ActionBarActivity implements OnItemClickListener, SearchSuggestionsAdapter.SuggestionDisplay, FilterQueryProvider {
    public final static String TAG = "VLC/MainActivity";

    public static final String ACTION_SHOW_PLAYER = "org.videolan.vlc.gui.ShowPlayer";

    private static final String PREF_FIRST_RUN = "first_run";

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;
    private static final int ACTIVITY_SHOW_INFOLAYOUT = 2;
    private static final int ACTIVITY_SHOW_PROGRESSBAR = 3;
    private static final int ACTIVITY_HIDE_PROGRESSBAR = 4;
    private static final int ACTIVITY_SHOW_TEXTINFO = 5;

    private ActionBar mActionBar;
    private SidebarAdapter mSidebarAdapter;
    private AudioPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private SlidingPaneLayout mSlidingPane;
    private HackyDrawerLayout mRootContainer;
    private ListView mListView;
    private ActionBarDrawerToggle mDrawerToggle;
    private View mSideMenu;

    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;
    private View mAudioPlayerFilling;
    private String mCurrentFragment;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;
    private boolean mFirstRun = false;
    private boolean mScanNeeded = false;

    private Handler mHandler = new MainActivityHandler(this);
    private int mFocusedPrior = 0;
    private int mActionBarIconId = -1;
    Menu mMenu;
    private SearchView mSearchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Theme must be applied before super.onCreate */
        applyTheme();

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

        /* Load media items from database and storage */
        MediaLibrary.getInstance().loadMediaItems();

        /*** Start initializing the UI ***/

        setContentView(R.layout.main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        mSlidingPane = (SlidingPaneLayout) findViewById(R.id.pane);
        mSlidingPane.setPanelSlideListener(mPanelSlideListener);

        mSideMenu = findViewById(R.id.side_menu);
        mListView = (ListView)findViewById(R.id.sidelist);
        mListView.setFooterDividersEnabled(true);
        mSidebarAdapter = new SidebarAdapter(this);
        mListView.setAdapter(mSidebarAdapter);

        if (savedInstanceState != null){
            mCurrentFragment = savedInstanceState.getString("current");
            if (mCurrentFragment != null)
                mSidebarAdapter.setCurrentFragment(mCurrentFragment);
        }


        /* Initialize UI variables */
        mInfoLayout = findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) findViewById(R.id.info_progress);
        mInfoText = (TextView) findViewById(R.id.info_text);
        mAudioPlayerFilling = findViewById(R.id.audio_player_filling);
        mRootContainer = (HackyDrawerLayout) findViewById(R.id.root_container);

        /* Set up the action bar */
        prepareActionBar();

        /* Set up the sidebar click listener
         * no need to invalidate menu for now */
        mDrawerToggle = new ActionBarDrawerToggle(this, mRootContainer, R.string.drawer_open, R.string.drawer_close){
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder) instanceof BrowserFragment)
                    ((BrowserFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder)).setReadyToDisplay(true);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mRootContainer.setDrawerListener(mDrawerToggle);
        // set a custom shadow that overlays the main content when the drawer opens
        mRootContainer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mListView.setOnItemClickListener(this);

        /* Set up the audio player */
        mAudioPlayer = new AudioPlayer();
        mAudioController = AudioServiceController.getInstance();

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.audio_player, mAudioPlayer)
            .commit();

        if (mFirstRun) {
            /*
             * The sliding menu is automatically opened when the user closes
             * the info dialog. If (for any reason) the dialog is not shown,
             * open the menu after a short delay.
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mRootContainer.openDrawer(mSideMenu);
                }
            }, 500);
        }

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);

        /* Reload the latest preferences */
        reloadPreferences();
    }

    private void applyTheme() {
        boolean enableBlackTheme = mSettings.getBoolean("enable_black_theme", false);
        if (enableBlackTheme) {
            setTheme(R.style.Theme_VLC_Black);
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
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAudioController.addAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().bindAudioService(this);

        /* FIXME: this is used to avoid having MainActivity twice in the backstack */
        if (getIntent().hasExtra(AudioService.START_FROM_NOTIFICATION))
            getIntent().removeExtra(AudioService.START_FROM_NOTIFICATION);


        /* Load media items from database and storage */
        if (mScanNeeded)
            MediaLibrary.getInstance().loadMediaItems();
   }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        // Figure out if currently-loaded fragment is a top-level fragment.
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);
        boolean found = false;
        if(current != null) {
            found = SidebarAdapter.sidebarFragments.contains(current.getTag());
        } else {
            found = true;
        }

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
        if(current == null || (!current.getTag().equals(mCurrentFragment) && found)) {
            Log.d(TAG, "Reloading displayed fragment");
            if(mCurrentFragment == null)
                mCurrentFragment = "video";
            if(!SidebarAdapter.sidebarFragments.contains(mCurrentFragment)) {
                Log.d(TAG, "Unknown fragment \"" + mCurrentFragment + "\", resetting to video");
                mCurrentFragment = "video";
            }
            Fragment ff = getFragment(mCurrentFragment);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_placeholder, ff, mCurrentFragment);
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
        mScanNeeded = MediaLibrary.getInstance().isWorking();
        /* Stop scanning for files */
        MediaLibrary.getInstance().stop();
        /* Save the tab status in pref */
        SharedPreferences.Editor editor = getSharedPreferences("MainActivity", MODE_PRIVATE).edit();
        editor.putString("fragment", mCurrentFragment);
        Util.commitPreferences(editor);

        mAudioController.removeAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().unbindAudioService(this);
        mFocusedPrior = 0;
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("current", mCurrentFragment);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {}
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
        if(mRootContainer.isDrawerOpen(mSideMenu)) {
            if (mFocusedPrior != 0)
                requestFocusOnSearch();
            mRootContainer.closeDrawer(mSideMenu);
            return;
        }

        // Slide down the audio player if it is shown entirely.
        if (slideDownAudioPlayer())
            return;

        if (mCurrentFragment!= null) {
            // If it's the directory view, a "backpressed" action shows a parent.
            if (mCurrentFragment.equals(SidebarEntry.ID_DIRECTORIES)) {
                DirectoryViewFragment directoryView = (DirectoryViewFragment) getFragment(mCurrentFragment);
                if (!directoryView.isRootDirectory()) {
                    directoryView.showParentDirectory();
                    return;
                }
            } else if (mCurrentFragment.equals(SidebarEntry.ID_NETWORK)){
                NetworkFragment networkFragment = (NetworkFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_placeholder);
                if (networkFragment !=null && !networkFragment.isRootDirectory()) {
                    networkFragment.goBack();
                    return;
                }
            }
        }
        finish();
    }

    private Fragment getFragment(String id)
    {
        Fragment frag = getSupportFragmentManager().findFragmentByTag(id);
        if (frag != null)
            return frag;
        return mSidebarAdapter.fetchFragment(id);
    }

    private static void ShowFragment(FragmentActivity activity, String tag, Fragment fragment, String previous) {
        if (fragment == null) {
            Log.e(TAG, "Cannot show a null fragment, ShowFragment("+tag+") aborted.");
            return;
        }

        FragmentManager fm = activity.getSupportFragmentManager();

        //abort if fragment is already the current one
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);
        if(current != null && current.getTag().equals(tag))
            return;

        //try to pop back if the fragment is already on the backstack
        if (fm.popBackStackImmediate(tag, 0))
            return;

        //fragment is not there yet, spawn a new one
        FragmentTransaction ft = fm.beginTransaction();
        ft.setCustomAnimations(R.anim.anim_enter_right, R.anim.anim_leave_left, R.anim.anim_enter_left, R.anim.anim_leave_right);
        ft.replace(R.id.fragment_placeholder, fragment, tag);
        ft.addToBackStack(previous);
        ft.commit();
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
        startActivity(i);
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

        if (LibVlcUtil.isFroyoOrLater()) {
            SearchManager searchManager =
                    (SearchManager) getSystemService(Context.SEARCH_SERVICE);
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
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
        // Disable the sort option if we can't use it on the current fragment.
        if (current == null || !(current instanceof ISortable)) {
            menu.findItem(R.id.ml_menu_sortby).setEnabled(false);
            menu.findItem(R.id.ml_menu_sortby).setVisible(false);
        }
        else {
            ISortable sortable = (ISortable) current;
            menu.findItem(R.id.ml_menu_sortby).setEnabled(true);
            menu.findItem(R.id.ml_menu_sortby).setVisible(true);
            MenuItem item = menu.findItem(R.id.ml_menu_sortby_name);
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

        boolean networkSave = current instanceof NetworkFragment && !((NetworkFragment)current).isRootDirectory();
        if (networkSave) {
            MenuItem item = menu.findItem(R.id.ml_menu_save);
            item.setVisible(true);
            String mrl = ((NetworkFragment)current).mMrl;
            item.setIcon(MediaDatabase.getInstance().networkFavExists(mrl) ?
                    R.drawable.ic_menu_bookmark_w :
                    R.drawable.ic_menu_bookmark_outline_w);

        }
        else
            menu.findItem(R.id.ml_menu_save).setVisible(false);
        if (current instanceof MRLPanelFragment)
            menu.findItem(R.id.ml_menu_clean).setVisible(!((MRLPanelFragment) current).isEmpty());
        boolean showLast = current instanceof AudioBrowserFragment || (current instanceof VideoGridFragment && mSettings.getString(PreferencesActivity.VIDEO_LAST, null) != null);
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(showLast);



        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

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
                showSecondaryFragment("equalizer");
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                if (!MediaLibrary.getInstance().isWorking()) {
                    if(current != null && current instanceof IRefreshable)
                        ((IRefreshable) current).refresh();
                    else
                        MediaLibrary.getInstance().loadMediaItems(this, true);
                }
                break;
            // Restore last playlist
            case R.id.ml_menu_last_playlist:
                if (current instanceof AudioBrowserFragment) {
                    Intent i = new Intent(AudioService.ACTION_REMOTE_LAST_PLAYLIST);
                    sendBroadcast(i);
                } else if (current instanceof VideoGridFragment) {
                    String location = Uri.decode(mSettings.getString(PreferencesActivity.VIDEO_LAST, null));
                    if (location != null)
                        VideoPlayerActivity.start(this, location);
                }
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
                if (getFragment(mCurrentFragment) instanceof MRLPanelFragment)
                    ((MRLPanelFragment)getFragment(mCurrentFragment)).clearHistory();
                break;
            case R.id.ml_menu_save:
                if (current == null)
                    break;
                ((NetworkFragment)current).toggleFavorite();
                item.setIcon(R.drawable.ic_menu_bookmark_w);
                break;
        }
        mRootContainer.closeDrawer(mSideMenu);
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN)
                MediaLibrary.getInstance().loadMediaItems(this, true);
            else if (resultCode == PreferencesActivity.RESULT_RESTART) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        }
    }

    public void setMenuFocusDown(boolean idIsEmpty, int id) {
        if (mMenu == null)
            return;
        //Save menu items ids for focus control
        final int[] menu_controls = new int[mMenu.size()+1];
        for (int i = 0 ; i < mMenu.size() ; i++){
            menu_controls[i] = mMenu.getItem(i).getItemId();
        }
        menu_controls[mMenu.size()] = mActionBarIconId;
        /*menu_controls = new int[]{R.id.ml_menu_search,
            R.id.ml_menu_open_mrl, R.id.ml_menu_sortby,
            R.id.ml_menu_last_playlist, R.id.ml_menu_refresh,
            mActionBarIconId};*/
        int pane = mSlidingPane.getState();
        for(int r : menu_controls) {
            View v = findViewById(r);
            if (v != null) {
                if (!idIsEmpty)
                    v.setNextFocusDownId(id);
                else {
                    if (pane ==  mSlidingPane.STATE_CLOSED) {
                        v.setNextFocusDownId(R.id.play_pause);
                    } else if (pane == mSlidingPane.STATE_OPENED) {
                        v.setNextFocusDownId(R.id.header_play_pause);
                    } else if (pane ==
                        mSlidingPane.STATE_OPENED_ENTIRELY) {
                        v.setNextFocusDownId(r);
                    }
                }
            }
        }
    }

    public void setSearchAsFocusDown(boolean idIsEmpty, View parentView, int id) {
        View playPause = findViewById(R.id.header_play_pause);

        if (!idIsEmpty) {
            View list = null;
            int pane = mSlidingPane.getState();

            if (parentView == null)
                list = findViewById(id);
            else
                list = parentView.findViewById(id);

            if (list != null) {
                if (pane == mSlidingPane.STATE_OPENED_ENTIRELY) {
                    list.setNextFocusDownId(id);
                } else if (pane == mSlidingPane.STATE_OPENED) {
                    list.setNextFocusDownId(R.id.header_play_pause);
                    playPause.setNextFocusUpId(id);
                }
            }
        } else {
           playPause.setNextFocusUpId(R.id.ml_menu_search);
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
        if (mFocusedPrior == 0)
            setMenuFocusDown(true, 0);
        if (getCurrentFocus() != null)
            mFocusedPrior = getCurrentFocus().getId();
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
        View v = getCurrentFocus();
        if (v == null)
            return super.onKeyUp(keyCode, event);
        if ((mActionBarIconId == -1) &&
            (v.getId() == -1)  &&
            (v.getNextFocusDownId() == -1) &&
            (v.getNextFocusUpId() == -1) &&
            (v.getNextFocusLeftId() == -1) &&
            (v.getNextFocusRightId() == -1)) {
            mActionBarIconId = Util.generateViewId();
            v.setId(mActionBarIconId);
            v.setNextFocusUpId(mActionBarIconId);
            v.setNextFocusDownId(mActionBarIconId);
            v.setNextFocusLeftId(mActionBarIconId);
            v.setNextFocusRightId(R.id.ml_menu_search);
            if (LibVlcUtil.isHoneycombOrLater())
                v.setNextFocusForwardId(mActionBarIconId);
            findViewById(R.id.ml_menu_search).setNextFocusLeftId(
                mActionBarIconId);
        }
        return super.onKeyUp(keyCode, event);
    }

    private void reloadPreferences() {
        SharedPreferences sharedPrefs = getSharedPreferences("MainActivity", MODE_PRIVATE);
        mCurrentFragment = sharedPrefs.getString("fragment", "video");
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(ACTION_SHOW_PLAYER)) {
                showAudioPlayer();
            }
        }
    };

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
                    ma.setSupportProgressBarIndeterminateVisibility(true);
                    ma.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                case ACTIVITY_HIDE_PROGRESSBAR:
                    ma.setSupportProgressBarIndeterminateVisibility(false);
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
                    }
                    else {
                    /* Slightly delay the appearance of the progress bar to avoid unnecessary flickering */
                        if (!hasMessages(ACTIVITY_SHOW_INFOLAYOUT)) {
                            Message m = new Message();
                            m.what = ACTIVITY_SHOW_INFOLAYOUT;
                            sendMessageDelayed(m, 300);
                        }
                    }
                    break;
            }
        }
    };

    public void hideKeyboard(){
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
                getWindow().getDecorView().getRootView().getWindowToken(), 0);
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

    /**
     * Show the audio player.
     */
    public void showAudioPlayer() {
        mActionBar.collapseActionView();
        // Open the pane only if is entirely opened.
        if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED_ENTIRELY)
            mSlidingPane.openPane();
        mAudioPlayerFilling.setVisibility(View.VISIBLE);
    }

    public int  getSlidingPaneState() {
            return mSlidingPane.getState();
    }

    /**
     * Slide down the audio player.
     * @return true on success else false.
     */
    public boolean slideDownAudioPlayer() {
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED) {
            mSlidingPane.openPane();
            return true;
        }
        return false;
    }

    /**
     * Slide up and down the audio player depending on its current state.
     */
    public void slideUpOrDownAudioPlayer() {
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED){
            mActionBar.show();
            mSlidingPane.openPane();
        } else if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED){
            mActionBar.hide();
            mSlidingPane.closePane();
        }
    }

    /**
     * Hide the audio player.
     */
    public void hideAudioPlayer() {
        mSlidingPane.openPaneEntirely();
        mAudioPlayerFilling.setVisibility(View.GONE);
    }

    private final SlidingPaneLayout.PanelSlideListener mPanelSlideListener
        = new SlidingPaneLayout.PanelSlideListener() {
        float previousOffset =  1.0f;
            @Override
            public void onPanelSlide(float slideOffset) {
                if (slideOffset >= 0.1 && slideOffset > previousOffset && !mActionBar.isShowing())
                    mActionBar.show();
                else if (slideOffset <= 0.1 && slideOffset < previousOffset && mActionBar.isShowing())
                    mActionBar.hide();
                previousOffset = slideOffset;
            }

            @Override
            public void onPanelOpened() {
                int resId = Util.getResourceFromAttribute(MainActivity.this, R.attr.mini_player_top_shadow);
                if (resId != 0)
                    mSlidingPane.setShadowResource(resId);
                mAudioPlayer.setHeaderVisibilities(false, false, true, true, true);
                mRootContainer.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_UNLOCKED);
                removeTipViewIfDisplayed();
                mAudioPlayer.showAudioPlayerTips();
            }

            @Override
            public void onPanelOpenedEntirely() {
                mSlidingPane.setShadowDrawable(null);
                mRootContainer.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_UNLOCKED);
            }

            @Override
            public void onPanelClosed() {
                mAudioPlayer.setHeaderVisibilities(true, true, false, false, false);
                mRootContainer.setDrawerLockMode(HackyDrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                mAudioPlayer.showPlaylistTips();
            }

    };

    /**
     * Show a tip view.
     * @param layoutId the layout of the tip view
     * @param settingKey the setting key to check if the view must be displayed or not.
     */
    public void showTipViewIfNeeded(final int layoutId, final String settingKey) {
        if (!mSettings.getBoolean(settingKey, false) && AndroidDevices.hasTsp()) {
            removeTipViewIfDisplayed();
            View v = LayoutInflater.from(this).inflate(layoutId, null);
            mRootContainer.addView(v,
                    new HackyDrawerLayout.LayoutParams(HackyDrawerLayout.LayoutParams.MATCH_PARENT,
                            HackyDrawerLayout.LayoutParams.MATCH_PARENT));

            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeTipViewIfDisplayed();
                }
            });

            TextView okGotIt = (TextView) v.findViewById(R.id.okgotit_button);
            okGotIt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeTipViewIfDisplayed();
                    Editor editor = mSettings.edit();
                    editor.putBoolean(settingKey, true);
                    Util.commitPreferences(editor);
                }
            });
        }
    }

    /**
     * Remove the current tip view if there is one displayed.
     */
    public void removeTipViewIfDisplayed() {
        if (mRootContainer.getChildCount() > 2){
            for (int i = 0 ; i< mRootContainer.getChildCount() ; ++i){
            if (mRootContainer.getChildAt(i).getId() == R.id.audio_tips)
                mRootContainer.removeViewAt(i);
            }
        }
    }

    public void onClick(View v){
        switch (v.getId()){
            case R.id.settings:
            case R.id.settings_icon:
                startActivityForResult(new Intent(this, PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
                break;
            case R.id.about:
            case R.id.about_icon:
                showSecondaryFragment("about");
                break;
        }
        mRootContainer.closeDrawer(mSideMenu);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        SidebarAdapter.SidebarEntry entry = (SidebarEntry) mListView.getItemAtPosition(position);
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);

        if(current == null || (entry != null && current.getTag().equals(entry.id))) { /* Already selected */
            if (mFocusedPrior != 0)
                requestFocusOnSearch();
            mRootContainer.closeDrawer(mSideMenu);
            return;
        }

        // This should not happen
        if(entry == null || entry.id == null)
            return;

        if (entry.type == SidebarEntry.TYPE_FRAGMENT) {

                /* Slide down the audio player */
            slideDownAudioPlayer();

                /* Switch the fragment */
            Fragment fragment = getFragment(entry.id);
            if (fragment instanceof BrowserFragment)
                ((BrowserFragment)fragment).setReadyToDisplay(false);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_placeholder, fragment, entry.id);
            ft.addToBackStack(mCurrentFragment);
            ft.commit();
            mCurrentFragment = entry.id;
            mSidebarAdapter.setCurrentFragment(mCurrentFragment);

            if (mFocusedPrior != 0)
                requestFocusOnSearch();
            mRootContainer.closeDrawer(mSideMenu);
        }else if (entry.attributeID == R.attr.ic_menu_preferences){
            startActivityForResult(new Intent(this, PreferencesActivity.class), ACTIVITY_RESULT_PREFERENCES);
        }
    }

    private void requestFocusOnSearch() {
        View search = findViewById(R.id.ml_menu_search);
        if (search != null)
            search.requestFocus();
    }
}
