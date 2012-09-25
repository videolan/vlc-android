/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

import java.util.ArrayList;

import org.videolan.vlc.AudioService;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.gui.SidebarAdapter.SidebarEntry;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.widget.AudioMiniPlayer;

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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.ImageView;
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

    private static final String PREF_SHOW_INFO = "show_info";

    private ActionBar mActionBar;
    private SlidingMenu mMenu;
    private SidebarAdapter mSidebarAdapter;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;
    private String mCurrentFragment;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!Util.hasCompatibleCPU()) {
            Log.e(TAG, Util.getErrorMsg());
            super.onCreate(savedInstanceState);
            Intent i = new Intent(this, CompatErrorActivity.class);
            startActivity(i);
            finish();
            return;
        }

        if (Util.isICSOrLater()) /* Bug on pre-ICS, the progress bar is always present */
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Set up the sliding menu
        setContentView(R.layout.sliding_menu);
        mMenu = (SlidingMenu) findViewById(R.id.sliding_menu);
        updateMenuOffset();

        View v_main = LayoutInflater.from(this).inflate(R.layout.main, null);
        mMenu.setViewAbove(v_main);
        View sidebar = LayoutInflater.from(this).inflate(R.layout.sidebar, null);
        ((ListView)sidebar).setFooterDividersEnabled(true);
        final ListView listView = (ListView)sidebar.findViewById(android.R.id.list);
        mSidebarAdapter = new SidebarAdapter(getSupportFragmentManager());
        listView.setAdapter(mSidebarAdapter);
        mMenu.setViewBehind(sidebar);

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        LibVLC.useIOMX(this);
        try {
            // Start LibVLC
            LibVLC.getInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
            super.onCreate(null);
            Intent i = new Intent(this, CompatErrorActivity.class);
            i.putExtra("runtimeError", true);
            i.putExtra("message", "LibVLC failed to initialize (LibVlcException)");
            startActivity(i);
            finish();
            return;
        }

        super.onCreate(savedInstanceState);

        /* Initialize variables */
        mInfoLayout = v_main.findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) v_main.findViewById(R.id.info_progress);
        mInfoText = (TextView) v_main.findViewById(R.id.info_text);

        /* Set up the action bar */
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        mActionBar.setDisplayHomeAsUpEnabled(true);

        /* Add padding between the home button and the arrow */
        ImageView home = (ImageView)findViewById(Util.isHoneycombOrLater()
                ? android.R.id.home : R.id.abs__home);
        if (home != null) 
            home.setPadding(20, 0, 0, 0);

        /* Set up the sidebar click listener */
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick (AdapterView<?> parent, View view,
                    int position, long id) {
                SidebarAdapter.SidebarEntry entry = (SidebarEntry) listView.getItemAtPosition(position);
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                if(current.getTag() == entry.id) /* Already selected */
                    return;

                /* Clear any backstack before switching tabs.
                 * This way it's more consistent for the user, who might have
                 * switched tabs and hit back to quit, only to activate an old
                 * backstack.
                 */
                if(getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    for(int i = 0; i < getSupportFragmentManager().getBackStackEntryCount(); i++) {
                        getSupportFragmentManager().popBackStack();
                    }
                }
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.detach(current);
                ft.attach(mSidebarAdapter.getFragment(entry.id));
                ft.commit();
                mCurrentFragment = entry.id;
                mMenu.showAbove();
            }
        });

        /* Set up the mini audio player */
        mAudioPlayer = new AudioMiniPlayer();
        mAudioController = AudioServiceController.getInstance();
        mAudioPlayer.setAudioPlayerControl(mAudioController);
        mAudioPlayer.update();

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.audio_mini_player, mAudioPlayer)
            .commit();

        /* Show info/alpha/beta Warning */
        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "package info not found.");
        }
        if (pinfo != null) {
            mVersionNumber = pinfo.versionCode;

            if (mSettings.getInt(PREF_SHOW_INFO, -1) != mVersionNumber)
                showInfoDialog();
        }

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        registerReceiver(messageReceiver, filter);

        /* Reload the latest preferences */
        reloadPreferences();

        /* Load media items from database and storage */
        MediaLibrary.getInstance(this).loadMediaItems(this);
    }

    private void updateMenuOffset() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        @SuppressWarnings("deprecation")
        int behindOffset_dp = Util.convertPxToDp(display.getWidth()) - 208;
        mMenu.setBehindOffset(Util.convertDpToPx(behindOffset_dp));
    }

    @Override
    protected void onResume() {
        mAudioController.addAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().bindAudioService(this);
        Boolean startFromNotification = getIntent().hasExtra(AudioService.START_FROM_NOTIFICATION);

        /* Restore last view */
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);
        boolean found = false;
        if(current != null) {
            for(int i = 0; i < SidebarAdapter.entries.size(); i++) {
                if(SidebarAdapter.entries.get(i).id == current.getTag()) {
                    found = true;
                    break;
                }
            }
        } else {
            found = true;
        }
        /* Don't call replace() on a non-sidebar fragment, since replace() will
         * remove() the currently displayed fragment and replace it with a
         * blank screen.
         */
        if(found) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_placeholder, mSidebarAdapter.getFragment(mCurrentFragment));
            ft.commit();
        }

        if (startFromNotification)
            getIntent().removeExtra(AudioService.START_FROM_NOTIFICATION);
        super.onResume();
    }

    /**
     * Stop audio player and save opened tab
     */
    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = getSharedPreferences("MainActivity", MODE_PRIVATE).edit();
        editor.putString("fragment", mCurrentFragment);
        editor.commit();
        mAudioController.removeAudioPlayer(mAudioPlayer);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {}
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        reloadPreferences();
    }

    @Override
    public void onBackPressed() {
        if(mMenu.isBehindShowing()) {
            /* Close the menu first */
            mMenu.showAbove();
        } else {
            super.onBackPressed();
        }
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateMenuOffset();
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

        // Intent to start new Activity
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
                startActivity(intent);
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                // TODO: factor this into each fragment
                if(mCurrentFragment.equals("directories")) {
                    DirectoryViewFragment directoryView = (DirectoryViewFragment) mSidebarAdapter.getFragment(mCurrentFragment);
                    directoryView.refresh();
                }
                else if(mCurrentFragment.equals("history"))
                    ((HistoryFragment)mSidebarAdapter.getFragment(mCurrentFragment)).refresh();
                else
                    MediaLibrary.getInstance(this).loadMediaItems(this);
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
                if(mMenu.isBehindShowing())
                    mMenu.showAbove();
                else
                    mMenu.showBehind();
                break;
        }
        return super.onOptionsItemSelected(item);
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
                infoDialog.dismiss();
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
                setProgressBarIndeterminateVisibility(Boolean.TRUE);
            } else if (action.equalsIgnoreCase(ACTION_HIDE_PROGRESSBAR)) {
                setProgressBarIndeterminateVisibility(Boolean.FALSE);
            } else if (action.equalsIgnoreCase(ACTION_SHOW_TEXTINFO)) {
                String info = intent.getStringExtra("info");
                int max = intent.getIntExtra("max", 0);
                int progress = intent.getIntExtra("progress", 100);
                mInfoText.setText(info);
                mInfoProgress.setMax(max);
                mInfoProgress.setProgress(progress);
                mInfoLayout.setVisibility(info != null ? View.VISIBLE : View.GONE);
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


    public static void clearTextInfo(Context context) {
        sendTextInfo(context, null, 0, 100);
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

    private void onOpenMRL() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
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
                      ArrayList<String> media = new ArrayList<String>();
                      media.add(s);
                      c.append(media);
                    }
                };
                task.execute();
            }
        }
        );
        b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                return;
                }});
        b.show();
    }
}
