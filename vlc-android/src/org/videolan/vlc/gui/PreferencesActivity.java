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

import java.io.File;
import java.util.ArrayList;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.DatabaseManager;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class PreferencesActivity extends PreferenceActivity {

    public final static String TAG = "VLC/PreferencesActivity";

    public final static String NAME = "VlcSharedPreferences";
    public final static String LAST_MEDIA = "LastMedia";
    public final static int RESULT_RESCAN = RESULT_FIRST_USER + 1;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Create onPrefChange
        Preference rootDirectoryPref = findPreference("directories_root");
        rootDirectoryPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference arg0) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PreferencesActivity.this);
                final String current_path = sharedPrefs.getString("directories_root",Environment.getExternalStorageDirectory().getAbsolutePath());

                final Dialog dialog = new Dialog(PreferencesActivity.this);
                dialog.setContentView(R.layout.root_selection);
                dialog.setCancelable(true);
                dialog.setTitle(R.string.filebrowser_root);

                ArrayList<String> extMounts = new ArrayList<String>();
                File mntDir = new File("/mnt");
                String[] mntDirSubdirs = mntDir.list();
                if(mntDirSubdirs != null) {
                    for(String s : mntDirSubdirs) {
                        if(s == null) continue;

                        // skip over irrelevant mounts
                        if(s.equals("asec") || s.equals("obb") || s.equals("secure") || s.equals("sdcard")) continue;

                        if(new File("/mnt/" + s).isDirectory()) {
                            extMounts.add("/mnt/" + s);
                        }
                    }
                }

                Boolean selected = false;

                RadioButton internal = (RadioButton)dialog.findViewById(R.id.internal_memory);
                internal.setTag(Environment.getExternalStorageDirectory().getAbsolutePath());
                final TextView other_path = (TextView)dialog.findViewById(R.id.other_path);
                other_path.setEnabled(false); //enable only when "other" is selected
                RadioButton other = (RadioButton)dialog.findViewById(R.id.other);
                other.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton arg0, boolean isClicked) {
                        other_path.setEnabled(isClicked);
                        if(isClicked)
                            other_path.requestFocus();
                        else
                            other_path.clearFocus();
                    }
                });

                // fill in the radio buttons
                if(current_path.equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                    internal.setChecked(true);
                    selected = true;
                }

                final RadioGroup radiogroup = (RadioGroup) dialog.findViewById(R.id.radiogroup);
                for (int i = 0; i < extMounts.size(); i++) {
                    RadioButton myRadioButton = new RadioButton(PreferencesActivity.this);
                    myRadioButton.setFocusable(true);
                    myRadioButton.setText(getString(R.string.external_mount) + " @ " + extMounts.get(i));
                    if(!selected && current_path.equals(extMounts.get(i))) {
                        myRadioButton.setChecked(true);
                        selected = true;
                    }
                    myRadioButton.setTag(extMounts.get(i));
                    radiogroup.addView(myRadioButton,i+1); // i+1 to keep "internal memory" on top
                }
                radiogroup.invalidate();

                if(!selected) {
                    other.setChecked(true);
                    other_path.setEnabled(true);
                    other_path.setText(current_path);
                    selected = true;
                }

                Button button_ok = (Button) dialog.findViewById(R.id.ok);
                button_ok.setOnClickListener(new Button.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String newRoot;

                        int radioButtonID = radiogroup.getCheckedRadioButtonId();
                        RadioButton radioButton = (RadioButton)radiogroup.findViewById(radioButtonID);
                        if(radioButton.getTag().equals("other")) {
                            TextView other_path = (TextView)dialog.findViewById(R.id.other_path);
                            newRoot = other_path.getText().toString();
                        } else {
                            newRoot = (String) radioButton.getTag();
                        }

                        // did it even change? if not, don't bother with rescan.
                        if(!newRoot.equals(current_path)) {
                            SharedPreferences.Editor editor = sharedPrefs.edit();
                            editor.putString("directories_root", newRoot);
                            editor.commit();

                            setResult(RESULT_RESCAN);
                        }
                        dialog.dismiss();
                    }
                });
                Button button_cancel = (Button) dialog.findViewById(R.id.cancel);
                button_cancel.setOnClickListener(new Button.OnClickListener() {
                    @Override
                    public void onClick(View v) { dialog.cancel(); }
                });

                dialog.show();
                return true;
            }});

        // Create onClickListen
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

        // Create onClickListen
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
                                        DatabaseManager db = DatabaseManager.getInstance(getApplicationContext());
                                        db.clearSearchhistory();
                                    }
                                })

                                .setNegativeButton(android.R.string.cancel, null).show();
                        return true;
                    }
                });

        // HW decoding
        CheckBoxPreference checkboxHW = (CheckBoxPreference) findPreference("enable_iomx");
        checkboxHW.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        CheckBoxPreference checkboxHW = (CheckBoxPreference) preference;
                        LibVLC.setIOMX(checkboxHW.isChecked());
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

        // Change verbosity (logcat)
        CheckBoxPreference checkboxVerbosity = (CheckBoxPreference) findPreference("enable_verbose_mode");
        checkboxVerbosity.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    LibVLC.getInstance().changeVerbosity((Boolean) newValue);
                } catch (LibVlcException e) {
                    Log.e(TAG, "Failed to change logs verbosity");
                    e.printStackTrace();
                    return true;
                }
                String newstatus = ((Boolean)newValue) ? "enabled" : "disabled";
                Log.i(TAG, "Verbosity mode is now " + newstatus);
                return true;
            }
        });

        // Audio output
        ListPreference aoutPref = (ListPreference) findPreference("aout");
        int aoutEntriesId = Util.isGingerbreadOrLater() ? R.array.aouts : R.array.aouts_froyo;
        int aoutEntriesIdValues = Util.isGingerbreadOrLater() ? R.array.aouts_values : R.array.aouts_values_froyo;
        aoutPref.setEntries(aoutEntriesId);
        aoutPref.setEntryValues(aoutEntriesIdValues);
        aoutPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                LibVLC.setAout(PreferencesActivity.this, Integer.valueOf((String) newValue), true);
                return true;
            }
        });

        // Attach debugging items
        Preference quitAppPref = findPreference("quit_app");
        quitAppPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                        return true;
                    }
                });
        Preference clearMediaPref = findPreference("clear_media_db");
        clearMediaPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        DatabaseManager.getInstance(getBaseContext()).emptyDatabase();
                        Toast.makeText(getBaseContext(), R.string.media_db_cleared, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
        EditTextPreference setLocalePref = (EditTextPreference) findPreference("set_locale");
        setLocalePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Toast.makeText(getBaseContext(), R.string.set_locale_popup, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
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
}
