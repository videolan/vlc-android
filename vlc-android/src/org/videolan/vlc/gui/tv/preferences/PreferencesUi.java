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
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesUi extends BasePreferenceFragment {

    public static final String KEY_ENABLE_TOUCH_PLAYER = "enable_touch_player";

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
        findPreference("force_list_portrait").setVisible(false);
        findPreference("enable_headset_detection").setVisible(false);
        findPreference("enable_steal_remote_control").setVisible(false);
        findPreference(KEY_ENABLE_TOUCH_PLAYER).setVisible(AndroidDevices.hasTsp());
        findPreference("tv_ui").setVisible(AndroidDevices.hasTsp());
        findPreference("lockscreen_cover").setVisible(false);
        findPreference("enable_black_theme").setVisible(false);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;
        switch (preference.getKey()){
            case "enable_headset_detection":
                ((PreferencesActivity)getActivity()).detectHeadset(((TwoStatePreference) preference).isChecked());
                return true;
            case "enable_steal_remote_control":
                PlaybackService.Client.restartService(getActivity());
                return true;
            case "force_list_portrait":
            case "tv_ui":
                ((PreferencesActivity) getActivity()).setRestart();
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
