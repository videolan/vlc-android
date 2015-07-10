/*****************************************************************************
 * PreferencesActivity.java
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.Drawable.ConstantState;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.videolan.libvlc.util.HWDecoderUtil;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.BitmapCache;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.BuildConfig;

@SuppressWarnings("deprecation")
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener, PlaybackService.Client.Callback {

    public final static String TAG = "VLC/PreferencesActivity";

    public final static String NAME = "VlcSharedPreferences";
    public final static String VIDEO_RESUME_TIME = "VideoResumeTime";
    public final static String VIDEO_PAUSED = "VideoPaused";
    public final static String VIDEO_SUBTITLE_FILES = "VideoSubtitleFiles";
    public final static String VIDEO_LAST = "VideoLastPlayed";
    public final static String VIDEO_SPEED = "VideoSpeed";
    public final static String VIDEO_BACKGROUND = "video_background";
    public final static String VIDEO_RESTORE = "video_restore";
    public final static int RESULT_RESCAN = RESULT_FIRST_USER + 1;
    public final static int RESULT_RESTART = RESULT_FIRST_USER + 2;

    private PlaybackService.Client mClient = new PlaybackService.Client(this, this);
    private PlaybackService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Theme must be applied before super.onCreate */
        applyTheme();

        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        if (!AndroidDevices.hasTsp()){
            findPreference("screen_orientation").setEnabled(false);
            findPreference("enable_black_theme").setEnabled(false);
            findPreference("ui_category").setEnabled(false);
        }

        // Directories
        Preference directoriesPref = findPreference("directories");
        directoriesPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(getApplicationContext(), SecondaryActivity.class);
                        intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER);
                        startActivity(intent);
                        setResult(RESULT_RESCAN);
                        return true;
                    }
                });

        // Screen orientation
        ListPreference screenOrientationPref = (ListPreference) findPreference("screen_orientation");
        screenOrientationPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PreferencesActivity.this);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("screen_orientation_value", (String)newValue);
                Util.commitPreferences(editor);
                return true;
            }
        });

        // Headset detection option
        final CheckBoxPreference checkboxHS = (CheckBoxPreference) findPreference("enable_headset_detection");
        checkboxHS.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mService != null)
                            mService.detectHeadset(checkboxHS.isChecked());
                        return true;
                    }
                });

        // Steal remote control
        Preference checkboxStealRC = findPreference("enable_steal_remote_control");
        checkboxStealRC.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        PlaybackService.Client.restartService(PreferencesActivity.this);
                        return true;
                    }
                });

        // Black theme
        Preference checkboxBlackTheme = findPreference("enable_black_theme");
        checkboxBlackTheme.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        setResult(RESULT_RESTART);
                        Intent intent = getIntent();
                        finish();
                        startActivity(intent);
                        return true;
                    }
                });

        // Clear search history
        Preference clearHistoryPref = findPreference("clear_history");
        clearHistoryPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(PreferencesActivity.this)
                                .setTitle(R.string.clear_history)
                                .setMessage(R.string.validation)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        MediaDatabase db = MediaDatabase.getInstance();
                                        db.clearSearchHistory();
                                    }
                                })

                                .setNegativeButton(android.R.string.cancel, null).show();
                        return true;
                    }
                });

        // Clear media library
        Preference clearMediaPref = findPreference("clear_media_db");
        clearMediaPref
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        MediaDatabase.getInstance().emptyDatabase();
                        BitmapCache.getInstance().clear();
                        AudioUtil.clearCacheFolders();
                        setResult(RESULT_RESCAN);
                        Util.snacker(getWindow().getDecorView().findViewById(android.R.id.content), R.string.media_db_cleared);
                        return true;
                    }
                });

        // Debug log activity
        Preference debugLogsPref = findPreference("debug_logs");
        debugLogsPref
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(getApplicationContext(), DebugLogActivity.class);
                        startActivity(intent);
                        return true;
                    }
                });

        /*** Attach debugging items **/
        Preference quitAppPref = findPreference("quit_app");
        if (BuildConfig.FLAVOR_target != "chrome") {
            quitAppPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {

                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                            return true;
                        }
                    });
        } else
            quitAppPref.setEnabled(false);

        // Audio output
        ListPreference aoutPref = (ListPreference) findPreference("aout");
        final HWDecoderUtil.AudioOutput aout = HWDecoderUtil.getAudioOutputFromDevice();
        if (aout == HWDecoderUtil.AudioOutput.AUDIOTRACK || aout == HWDecoderUtil.AudioOutput.OPENSLES) {
            /* no AudioOutput choice */
            PreferenceGroup group = (PreferenceGroup) findPreference("advanced_prefs_group");
            group.removePreference(aoutPref);
        } else {
            int aoutEntriesId = R.array.aouts;
            int aoutEntriesIdValues = R.array.aouts_values;
            aoutPref.setEntries(aoutEntriesId);
            aoutPref.setEntryValues(aoutEntriesIdValues);
            final String value = aoutPref.getValue();
            if (value == null)
                aoutPref.setValue(String.valueOf(VLCOptions.AOUT_AUDIOTRACK));
            else {
                /* number of entries decreased, handle old values */
                final int intValue = Integer.parseInt(value);
                if (intValue != VLCOptions.AOUT_AUDIOTRACK && intValue != VLCOptions.AOUT_OPENSLES)
                    aoutPref.setValue(String.valueOf(VLCOptions.AOUT_AUDIOTRACK));
            }
        }
        // Video output
//        FIXME : This setting is disable until OpenGL it's fixed
//        ListPreference voutPref = (ListPreference) findPreference("vout");
//        int voutEntriesId = LibVlcUtil.isGingerbreadOrLater() ? R.array.vouts : R.array.vouts_froyo;
//        int voutEntriesIdValues = LibVlcUtil.isGingerbreadOrLater() ? R.array.vouts_values : R.array.vouts_values_froyo;
//        voutPref.setEntries(voutEntriesId);
//        voutPref.setEntryValues(voutEntriesIdValues);
//        if (voutPref.getValue() == null)
//            voutPref.setValue("0"  VOUT_ANDROID_SURFACE );
        // Set locale
        EditTextPreference setLocalePref = (EditTextPreference) findPreference("set_locale");
        setLocalePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Util.snacker(getWindow().getDecorView().findViewById(android.R.id.content), R.string.set_locale_popup);
                return true;
            }
        });
        // Network caching
        EditTextPreference networkCachingPref = (EditTextPreference) findPreference("network_caching");
        networkCachingPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PreferencesActivity.this);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                try {
                    editor.putInt("network_caching_value", Integer.parseInt((String)newValue));
                } catch(NumberFormatException e) {
                    editor.putInt("network_caching_value", 0);
                    editor.putString("network_caching", "0");
                }
                Util.commitPreferences(editor);
                return true;
            }
        });

        /*** SharedPreferences Listener to apply changes ***/
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mClient.disconnect();
    }

    private void applyTheme() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableBlackTheme = pref.getBoolean("enable_black_theme", false);
        if (enableBlackTheme) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Toolbar bar;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            LinearLayout root = (LinearLayout) getListView().getParent().getParent().getParent();
            bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.toolbar, root, false);
            root.addView(bar, 0); // insert at top
        } else {
            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            if (!(root.getChildAt(0) instanceof ListView))
                return;
            ListView content = (ListView) root.getChildAt(0);

            root.removeAllViews();

            bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.toolbar, root, false);
            root.addView(bar);

            int height;
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
                height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }else{
                height = bar.getHeight();
            }

            content.setPadding(0, height, 0, 0);

            root.addView(content);
        }
        bar.setTitle(R.string.preferences);

        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equalsIgnoreCase("hardware_acceleration")
                || key.equalsIgnoreCase("subtitle_text_encoding")
                || key.equalsIgnoreCase("aout")
                || key.equalsIgnoreCase("vout")
                || key.equalsIgnoreCase("chroma_format")
                || key.equalsIgnoreCase("deblocking")
                || key.equalsIgnoreCase("enable_frame_skip")
                || key.equalsIgnoreCase("enable_time_stretching_audio")
                || key.equalsIgnoreCase("enable_verbose_mode")
                || key.equalsIgnoreCase("network_caching")
                || key.equalsIgnoreCase("dev_hardware_decoder")) {
            VLCInstance.restart(this);
            if (mService != null)
                mService.restartMediaPlayer();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof PreferenceScreen)
            setUpNestedScreen((PreferenceScreen) preference);
        try {
            if (preference!=null && preference instanceof PreferenceScreen) {
                Dialog dialog = ((PreferenceScreen)preference).getDialog();
                if (dialog!=null) {
                    Window window = dialog.getWindow();
                    if (window != null) {
                        ConstantState state = this.getWindow().getDecorView().findViewById(android.R.id.content).getBackground().getConstantState();
                        if (state != null)
                            window.getDecorView().setBackgroundDrawable(state.newDrawable());
                    }
                }
            }
        } catch(Exception e){}
        return false;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    public void setUpNestedScreen(PreferenceScreen preferenceScreen) {
        final Dialog dialog = preferenceScreen.getDialog();

        Toolbar bar;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            LinearLayout root = (LinearLayout) dialog.findViewById(android.R.id.list).getParent();
            bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.toolbar, root, false);
            root.addView(bar, 0); // insert at top
        } else {
            ViewGroup root = (ViewGroup) dialog.findViewById(android.R.id.content);
            ListView content = (ListView) root.getChildAt(0);

            root.removeAllViews();

            bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.toolbar, root, false);

            int height;
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
                height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }else{
                height = bar.getHeight();
            }

            content.setPadding(0, height, 0, 0);

            root.addView(content);
            root.addView(bar);
        }

        bar.setTitle(preferenceScreen.getTitle());

        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }
}
