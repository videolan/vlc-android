/*****************************************************************************
 * PreferencesActivity.java
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

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.AudioService;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.BitmapCache;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    public final static String TAG = "VLC/PreferencesActivity";

    public final static String NAME = "VlcSharedPreferences";
    public final static String LAST_MEDIA = "LastMedia";
    public final static String VIDEO_RESUME_TIME = "VideoResumeTime";
    public final static int RESULT_RESCAN = RESULT_FIRST_USER + 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Directories
        Preference directoriesPref = findPreference("directories");
        directoriesPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(getApplicationContext(), BrowserActivity.class);
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
                editor.commit();
                return true;
            }
        });

        // Headset detection option
        CheckBoxPreference checkboxHS = (CheckBoxPreference) findPreference("enable_headset_detection");
        checkboxHS.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        CheckBoxPreference checkboxHS = (CheckBoxPreference) preference;
                        AudioServiceController.getInstance().detectHeadset(checkboxHS.isChecked());
                        return true;
                    }
                });

        // Steal remote control
        Preference checkboxStealRC = findPreference("enable_steal_remote_control");
        checkboxStealRC.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        restartService(preference.getContext());
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
                                MediaDatabase db = MediaDatabase.getInstance(getApplicationContext());
                                db.clearSearchhistory();
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
                        MediaDatabase.getInstance(getBaseContext()).emptyDatabase();
                        BitmapCache.getInstance().clear();
                        Toast.makeText(getBaseContext(), R.string.media_db_cleared, Toast.LENGTH_SHORT).show();
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
        quitAppPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                        return true;
                    }
                });
        // Audio output
        ListPreference aoutPref = (ListPreference) findPreference("aout");
        int aoutEntriesId = Util.isGingerbreadOrLater() ? R.array.aouts : R.array.aouts_froyo;
        int aoutEntriesIdValues = Util.isGingerbreadOrLater() ? R.array.aouts_values : R.array.aouts_values_froyo;
        aoutPref.setEntries(aoutEntriesId);
        aoutPref.setEntryValues(aoutEntriesIdValues);
        if (aoutPref.getValue() == null)
            aoutPref.setValue(Util.isGingerbreadOrLater()
                    ? "2"/*AOUT_OPENSLES*/
                            : "0"/*AOUT_AUDIOTRACK_JAVA*/);
        // Set locale
        EditTextPreference setLocalePref = (EditTextPreference) findPreference("set_locale");
        setLocalePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Toast.makeText(getBaseContext(), R.string.set_locale_popup, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        /*** SharedPreferences Listener to apply changes ***/
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equalsIgnoreCase("enable_iomx")
                || key.equalsIgnoreCase("subtitles_text_encoding")
                || key.equalsIgnoreCase("aout")
                || key.equalsIgnoreCase("enable_time_stretching_audio")
                || key.equalsIgnoreCase("chroma_format")
                || key.equalsIgnoreCase("enable_verbose_mode")) {
            Util.updateLibVlcSettings(sharedPreferences);
            LibVLC.restart(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    private void restartService(Context context) {
        Intent service = new Intent(context, AudioService.class);

        AudioServiceController.getInstance().unbindAudioService(PreferencesActivity.this);
        context.stopService(service);

        context.startService(service);
        AudioServiceController.getInstance().bindAudioService(PreferencesActivity.this);
    }
}
