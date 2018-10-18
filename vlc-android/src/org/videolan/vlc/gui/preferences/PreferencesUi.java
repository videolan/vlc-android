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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.LocalePair;



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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareLocaleList();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;
        switch (preference.getKey()){
            case "tv_ui":
                ((PreferencesActivity) getActivity()).setRestartApp();
                return true;
            case "enable_black_theme":
                ((PreferencesActivity) getActivity()).exitAndRescan();
                return true;
            case "daynight":
                AppCompatDelegate.setDefaultNightMode(((SwitchPreferenceCompat)preference).isChecked() ? AppCompatDelegate.MODE_NIGHT_AUTO : AppCompatDelegate.MODE_NIGHT_NO);
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("set_locale")) UiTools.restartDialog(getActivity());
        else if (key.equals("browser_show_all_files")) ((PreferencesActivity) getActivity()).setRestart();
    }

    private void prepareLocaleList() {
        final LocalePair localePair = UiTools.getLocalesUsedInProject(getActivity());
        final ListPreference lp = (ListPreference)findPreference("set_locale");
        lp.setEntries(localePair.getLocaleEntries());
        lp.setEntryValues(localePair.getLocaleEntryValues());
    }
}
