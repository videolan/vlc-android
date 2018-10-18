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

package org.videolan.vlc.gui.tv.preferences;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.ListPreference;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.LocalePair;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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

        findPreference("enable_clone_mode").setVisible(false);
        findPreference("tv_ui").setVisible(AndroidDevices.hasTsp);
        findPreference("enable_black_theme").setVisible(false);
        findPreference("secondary_display_category").setVisible(false);
        findPreference("secondary_display_category_summary").setVisible(false);
        findPreference("daynight").setVisible(false);
        findPreference("blurred_cover_background").setVisible(false);
        prepareLocaleList();
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "set_locale":
                    UiTools.restartDialog(getActivity());
                    break;
                case "tv_ui":
                    ((PreferencesActivity) getActivity()).setRestartApp();
                    break;
                case "browser_show_all_files":
                    ((PreferencesActivity) getActivity()).setRestart();
                    break;
            }
    }

    private void prepareLocaleList() {
        LocalePair localePair = UiTools.getLocalesUsedInProject(getActivity());
        ListPreference lp = (ListPreference)findPreference("set_locale");
        lp.setEntries(localePair.getLocaleEntries());
        lp.setEntryValues(localePair.getLocaleEntryValues());
    }
}
