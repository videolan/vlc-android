/*
 * *************************************************************************
 *  PreferencesUi.java
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

import android.content.SharedPreferences;
import android.os.Bundle;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.LocalePair;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;



public class PreferencesUi extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected int getXml() {
        return R.xml.preferences_ui;
    }

    @Override
    protected int getTitleId() {
        return R.string.interface_prefs_screen;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findPreference("resume_playback").setVisible(AndroidDevices.isPhone);
        prepareLocaleList();
        setupTheme();
    }

    private void setupTheme() {
        final SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        if (!prefs.contains("app_theme")) {
            int theme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (prefs.getBoolean("daynight", false) && !AndroidDevices.canUseSystemNightMode()) {
                theme = AppCompatDelegate.MODE_NIGHT_AUTO;
            } else if (prefs.contains("enable_black_theme")) {
                if (prefs.getBoolean("enable_black_theme", false))
                theme = AppCompatDelegate.MODE_NIGHT_YES;
                else theme = AppCompatDelegate.MODE_NIGHT_NO;
            }
            prefs.edit().putString("app_theme", String.valueOf(theme)).apply();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null) return false;
        switch (preference.getKey()){
            case "tv_ui":
                ((PreferencesActivity) getActivity()).setRestartApp();
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "set_locale":
                UiTools.restartDialog(getActivity());
                break;
            case "browser_show_all_files":
                ((PreferencesActivity) getActivity()).setRestart();
                break;
            case "app_theme":
                ((PreferencesActivity) getActivity()).exitAndRescan();
                break;
        }
    }

    private void prepareLocaleList() {
        final LocalePair localePair = UiTools.getLocalesUsedInProject(getActivity());
        final ListPreference lp = (ListPreference)findPreference("set_locale");
        lp.setEntries(localePair.getLocaleEntries());
        lp.setEntryValues(localePair.getLocaleEntryValues());
    }
}
