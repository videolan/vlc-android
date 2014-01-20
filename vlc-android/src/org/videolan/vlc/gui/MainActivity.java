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

import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.AudioService;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.SidebarAdapter.SidebarEntry;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.widget.AudioMiniPlayer;
import org.videolan.vlc.widget.SlidingPaneLayout;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.slidingmenu.lib.SlidingMenu;

public class MainActivity extends SherlockFragmentActivity {
    public final static String TAG = "VLC/MainActivity";

    protected static final String ACTION_SHOW_PROGRESSBAR = "org.videolan.vlc.gui.ShowProgressBar";
    protected static final String ACTION_HIDE_PROGRESSBAR = "org.videolan.vlc.gui.HideProgressBar";
    protected static final String ACTION_SHOW_TEXTINFO = "org.videolan.vlc.gui.ShowTextInfo";
    public static final String ACTION_SHOW_PLAYER = "org.videolan.vlc.gui.ShowPlayer";

    private static final String PREF_SHOW_INFO = "show_info";
    private static final String PREF_FIRST_RUN = "first_run";

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;
    private static final int ACTIVITY_SHOW_INFOLAYOUT = 2;

    private ActionBar mActionBar;
    private SlidingMenu mMenu;
    private SidebarAdapter mSidebarAdapter;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private SlidingPaneLayout mSlidingPane;

    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;
    private View mAudioPlayerFilling;
    private String mCurrentFragment;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;
    private boolean mFirstRun = false;
    private boolean mScanNeeded = true;

    private Handler mHandler = new MainActivityHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!LibVlcUtil.hasCompatibleCPU(this)) {
            Log.e(TAG, LibVlcUtil.getErrorMsg());
            Intent i = new Intent(this, CompatErrorActivity.class);
            startActivity(i);
            finish();
            super.onCreate(savedInstanceState);
            return;
        }

        /* Get the current version from package */
        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "package info not found.");
        }
        if (pinfo != null)
            mVersionNumber = pinfo.versionCode;

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Check if it's the first run */
        mFirstRun = mSettings.getInt(PREF_FIRST_RUN, -1) != mVersionNumber;
        if (mFirstRun) {
            Editor editor = mSettings.edit();
            editor.putInt(PREF_FIRST_RUN, mVersionNumber);
            editor.commit();
        }

        try {
            // Start LibVLC
            Util.getLibVlcInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
            Intent i = new Intent(this, CompatErrorActivity.class);
            i.putExtra("runtimeError", true);
            i.putExtra("message", "LibVLC failed to initialize (LibVlcException)");
            startActivity(i);
            finish();
            super.onCreate(savedInstanceState);
            return;
        }

        super.onCreate(savedInstanceState);

        /*** Start initializing the UI ***/

        /* Enable the indeterminate progress feature */
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Set up the sliding menu
        setContentView(R.layout.sliding_menu);
        mMenu = (SlidingMenu) findViewById(R.id.sliding_menu);
        changeMenuOffset();

        View v_main = LayoutInflater.from(this).inflate(R.layout.main, null);
        mMenu.setContent(v_main);

        mSlidingPane = (SlidingPaneLayout) v_main.findViewById(R.id.pane);
        mSlidingPane.setPanelSlideListener(mPanelSlideListener);

        View sidebar = LayoutInflater.from(this).inflate(R.layout.sidebar, null);
        final ListView listView = (ListView)sidebar.findViewById(android.R.id.list);
        listView.setFooterDividersEnabled(true);
        mSidebarAdapter = new SidebarAdapter();
        listView.setAdapter(mSidebarAdapter);
        mMenu.setMenu(sidebar);

        /* Initialize UI variables */
        mInfoLayout = v_main.findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) v_main.findViewById(R.id.info_progress);
        mInfoText = (TextView) v_main.findViewById(R.id.info_text);
        mAudioPlayerFilling = v_main.findViewById(R.id.audio_mini_player_filling);

        /* Set up the action bar */
        prepareActionBar();

        /* Set up the sidebar click listener */
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                SidebarAdapter.SidebarEntry entry = (SidebarEntry) listView.getItemAtPosition(position);
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);

                if(current == null || current.getTag().equals(entry.id)) { /* Already selected */
                    mMenu.showContent();
                    return;
                }

                /*
                 * Clear any backstack before switching tabs. This avoids
                 * activating an old backstack, when a user hits the back button
                 * to quit
                 */
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                /* Hide the mini player */
                if(mSlidingPane.getState() == mSlidingPane.STATE_CLOSED)
                    mSlidingPane.openPane();

                /* Switch the fragment */
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_placeholder, getFragment(entry.id), entry.id);
                ft.commit();
                mCurrentFragment = entry.id;

                /*
                 * Set user visibility hints to work around weird Android
                 * behaviour of duplicate context menu events.
                 */
                current.setUserVisibleHint(false);
                getFragment(mCurrentFragment).setUserVisibleHint(true);
                // HACK ALERT: Set underlying audio browser to be invisible too.
                if(current.getTag().equals("tracks"))
                    getFragment("audio").setUserVisibleHint(false);

                mMenu.showContent();
            }
        });

        /* Set up the mini audio player */
        mAudioPlayer = new AudioMiniPlayer();
        mAudioController = AudioServiceController.getInstance();

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.audio_mini_player, mAudioPlayer)
            .commit();

        /* Show info/alpha/beta Warning */
        if (mSettings.getInt(PREF_SHOW_INFO, -1) != mVersionNumber)
            showInfoDialog();
        else if (mFirstRun) {
            /*
             * The sliding menu is automatically opened when the user closes
             * the info dialog. If (for any reason) the dialog is not shown,
             * open the menu after a short delay.
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mMenu.showMenu();
                }
            }, 500);
        }

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);

        /* Reload the latest preferences */
        reloadPreferences();
    }

    private void changeMenuOffset() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        @SuppressWarnings("deprecation")
        int behindOffset_dp = Util.convertPxToDp(display.getWidth()) - 208;
        mMenu.setBehindOffset(Util.convertDpToPx(behindOffset_dp));
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayHomeAsUpEnabled(true);
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
            MediaLibrary.getInstance(this).loadMediaItems(this);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        // Figure out if currently-loaded fragment is a top-level fragment.
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);
        boolean found = false;
        if(current != null) {
            for(int i = 0; i < SidebarAdapter.entries.size(); i++) {
                if(SidebarAdapter.entries.get(i).id.equals(current.getTag())) {
                    found = true;
                    break;
                }
            }
        } else {
            found = true;
        }

        /**
         * Let's see if Android recreated anything for us in the bundle.
         * Prevent duplicate creation of fragments, since mSidebarAdapter might
         * have been purged (losing state) when this activity was killed.
         */
        for(int i = 0; i < SidebarAdapter.entries.size(); i++) {
            String fragmentTag = SidebarAdapter.entries.get(i).id;
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(fragmentTag);
            if(fragment != null) {
                Log.d(TAG, "Restoring automatically recreated fragment \"" + fragmentTag + "\"");
                mSidebarAdapter.restoreFragment(fragmentTag, fragment);
            }
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
        mScanNeeded = MediaLibrary.getInstance(this).isWorking();
        /* Stop scanning for files */
        MediaLibrary.getInstance(this).stop();
        /* Save the tab status in pref */
        SharedPreferences.Editor editor = getSharedPreferences("MainActivity", MODE_PRIVATE).edit();
        editor.putString("fragment", mCurrentFragment);
        editor.commit();

        mAudioController.removeAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().unbindAudioService(this);
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
        if(mMenu.isMenuShowing()) {
            /* Close the menu first */
            mMenu.showContent();
            return;
        }

        // Slide down the mini player if it is shown entirely.
        if (mSlidingPane.getState() == mSlidingPane.STATE_CLOSED) {
            mSlidingPane.openPane();
            return;
        }

        // If it's the directory view, a "backpressed" action shows a parent.
        if (mCurrentFragment.equals("directories")) {
            DirectoryViewFragment directoryView = (DirectoryViewFragment) getFragment(mCurrentFragment);
            if (!directoryView.isRootDirectory()) {
                directoryView.showParentDirectory();
                return;
            }
        }
        super.onBackPressed();
    }

    private Fragment getFragment(String id)
    {
        return mSidebarAdapter.fetchFragment(id);
    }

    private void ShowFragment(String tag, Class<? extends Fragment> fragmentClass) {
        Fragment fragment = null;
        try {
            fragment = fragmentClass.newInstance();
        } catch (InstantiationException e) {
            Log.e(TAG, "Failed to instantiate "+fragmentClass.getName()+", ShowFragment("+tag+") aborted.");
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Failed to instantiate "+fragmentClass.getName()+", ShowFragment("+tag+") aborted.");
        }
        ShowFragment(this, tag, fragment);
    }

    public static void ShowFragment(FragmentActivity activity, String tag, Fragment fragment) {
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
        ft.addToBackStack(tag);
        ft.commit();
    }

    /** Create menu from XML
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Note: on Android 3.0+ with an action bar this method
         * is called while the view is created. This can happen
         * any time after onCreate.
         */
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.media_library, menu);
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
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeMenuOffset();
    }

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivity(intent);
        return false;
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Intent to start a new Activity
        Intent intent;

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                if (current == null)
                    break;
                if (current instanceof ISortable)
                    ((ISortable) current).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
                    ? VideoListAdapter.SORT_BY_TITLE
                    : VideoListAdapter.SORT_BY_LENGTH);
                break;
            // About
            case R.id.ml_menu_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            // Preferences
            case R.id.ml_menu_preferences:
                intent = new Intent(this, PreferencesActivity.class);
                startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES);
                break;
            case R.id.ml_menu_equalizer:
                ShowFragment("equalizer", EqualizerFragment.class);
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                // TODO: factor this into each fragment
                if(mCurrentFragment.equals("directories")) {
                    DirectoryViewFragment directoryView = (DirectoryViewFragment) getFragment(mCurrentFragment);
                    directoryView.refresh();
                }
                else if(mCurrentFragment.equals("history"))
                    ((HistoryFragment) getFragment(mCurrentFragment)).refresh();
                else
                    MediaLibrary.getInstance(this).loadMediaItems(this, true);
                break;
            // Restore last playlist
            case R.id.ml_menu_last_playlist:
                Intent i = new Intent(AudioService.ACTION_REMOTE_LAST_PLAYLIST);
                sendBroadcast(i);
                break;
            // Open MRL
            case R.id.ml_menu_open_mrl:
                onOpenMRL();
                break;
            case R.id.ml_menu_search:
                onSearchRequested();
                break;
            case android.R.id.home:
                /* Toggle the sidebar */
                if(mMenu.isMenuShowing())
                    mMenu.showContent();
                else
                    mMenu.showMenu();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN)
                MediaLibrary.getInstance(this).loadMediaItems(this, true);
        }
    }

    private void reloadPreferences() {
        SharedPreferences sharedPrefs = getSharedPreferences("MainActivity", MODE_PRIVATE);
        mCurrentFragment = sharedPrefs.getString("fragment", "video");
    }

    private void showInfoDialog() {
        final Dialog infoDialog = new Dialog(this, R.style.info_dialog);
        infoDialog.setContentView(R.layout.info_dialog);
        Button okButton = (Button) infoDialog.findViewById(R.id.ok);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox notShowAgain =
                        (CheckBox) infoDialog.findViewById(R.id.not_show_again);
                if (notShowAgain.isChecked() && mSettings != null) {
                    Editor editor = mSettings.edit();
                    editor.putInt(PREF_SHOW_INFO, mVersionNumber);
                    editor.commit();
                }
                /* Close the dialog */
                infoDialog.dismiss();
                /* and finally open the sliding menu if first run */
                if (mFirstRun)
                    mMenu.showMenu();
            }
        });
        infoDialog.show();
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void searchClick(View view) {
        onSearchRequested();
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(ACTION_SHOW_PROGRESSBAR)) {
                setSupportProgressBarIndeterminateVisibility(true);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (action.equalsIgnoreCase(ACTION_HIDE_PROGRESSBAR)) {
                setSupportProgressBarIndeterminateVisibility(false);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (action.equalsIgnoreCase(ACTION_SHOW_TEXTINFO)) {
                String info = intent.getStringExtra("info");
                int max = intent.getIntExtra("max", 0);
                int progress = intent.getIntExtra("progress", 100);
                mInfoText.setText(info);
                mInfoProgress.setMax(max);
                mInfoProgress.setProgress(progress);

                if (info == null) {
                    /* Cancel any upcoming visibility change */
                    mHandler.removeMessages(ACTIVITY_SHOW_INFOLAYOUT);
                    mInfoLayout.setVisibility(View.GONE);
                }
                else {
                    /* Slightly delay the appearance of the progress bar to avoid unnecessary flickering */
                    if (!mHandler.hasMessages(ACTIVITY_SHOW_INFOLAYOUT)) {
                        Message m = new Message();
                        m.what = ACTIVITY_SHOW_INFOLAYOUT;
                        mHandler.sendMessageDelayed(m, 300);
                    }
                }
            } else if (action.equalsIgnoreCase(ACTION_SHOW_PLAYER)) {
                showMiniPlayer();
            }
        }
    };

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
            }
        }
    };

    public static void showProgressBar(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void hideProgressBar(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_HIDE_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void sendTextInfo(Context context, String info, int progress, int max) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_TEXTINFO);
        intent.putExtra("info", info);
        intent.putExtra("progress", progress);
        intent.putExtra("max", max);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void clearTextInfo(Context context) {
        sendTextInfo(context, null, 0, 100);
    }

    private void onOpenMRL() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        b.setTitle(R.string.open_mrl_dialog_title);
        b.setMessage(R.string.open_mrl_dialog_msg);
        b.setView(input);
        b.setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {

                /* Start this in a new thread as to not block the UI thread */
                VLCCallbackTask task = new VLCCallbackTask(MainActivity.this)
                {
                    @Override
                    public void run() {
                      AudioServiceController c = AudioServiceController.getInstance();
                      String s = input.getText().toString();

                      /* Use the audio player by default. If a video track is
                       * detected, then it will automatically switch to the video
                       * player. This allows us to support more types of streams
                       * (for example, RTSP and TS streaming) where ES can be
                       * dynamically adapted rather than a simple scan.
                       */
                      c.load(s, false);
                    }
                };
                task.execute();
            }
        }
        );
        b.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                return;
                }});
        b.show();
    }

    /**
     * Show the mini player.
     */
    public void showMiniPlayer() {
        // Open the pane only if is entirely opened.
        if (mSlidingPane.getState() == mSlidingPane.STATE_OPENED_ENTIRELY)
            mSlidingPane.openPane();
        mAudioPlayerFilling.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the mini player.
     */
    public void hideMiniPlayer() {
        mSlidingPane.openPaneEntirely();
        mAudioPlayerFilling.setVisibility(View.GONE);
    }

    private final SlidingPaneLayout.PanelSlideListener mPanelSlideListener
        = new SlidingPaneLayout.PanelSlideListener() {

            @Override
            public void onPanelSlide(float slideOffset) {}

            @Override
            public void onPanelOpened() {
                mAudioPlayer.setHeaderVisibilities(false, false, true, true);
            }

            @Override
            public void onPanelOpenedEntirely() {}

            @Override
            public void onPanelClosed() {
                mAudioPlayer.setHeaderVisibilities(true, true, false, false);
            }

    };
}
