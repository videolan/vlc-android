/*
 * *************************************************************************
 *  PreferencesFragment.java
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

package org.videolan.vlc.gui.preferences;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;

public class PreferencesFragment extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String TAG = "VLC/PreferencesFragment";

    @Override
    protected int getXml() {
        return R.xml.preferences;
    }

    @Override
    protected int getTitleId() {
        return R.string.preferences;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "on create");

        if (!AndroidDevices.hasTsp()){
            findPreference("screen_orientation").setEnabled(false);
            findPreference("enable_black_theme").setEnabled(false);
            findPreference("ui_category").setEnabled(false);
        }

        // Screen orientation
        ListPreference screenOrientationPref = (ListPreference) findPreference("screen_orientation");
        screenOrientationPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("screen_orientation_value", (String) newValue);
                Util.commitPreferences(editor);
                return true;
            }
        });

        /*** SharedPreferences Listener to apply changes ***/
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equalsIgnoreCase("hardware_acceleration")
                || key.equalsIgnoreCase("subtitle_text_encoding")) {
            VLCInstance.restart(getActivity());
            ((PreferencesActivity)getActivity()).restartMediaPlayer();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        Fragment fragment = null;
        switch (preference.getKey()){
            case "directories":
                Intent intent = new Intent(VLCApplication.getAppContext(), SecondaryActivity.class);
                intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER);
                startActivity(intent);
                getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                return true;
            case "enable_black_theme":
                ((PreferencesActivity) getActivity()).exitAndRescan();
                return true;
            case "ui_category":
                fragment = new PreferencesUi();
                break;
            case "perf_category":
                fragment = new PreferencesPerformances();
                break;
            case "adv_category":
                fragment = new Advanced();
                break;
            case "dev_category":
                fragment = new Developer();
                break;
            default:
                return super.onPreferenceTreeClick(preference);
        }
        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_placeholder, fragment)
                .addToBackStack("main")
                .commit();
        return true;
    }
}
