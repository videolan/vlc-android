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

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;

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

        findPreference("tv_ui").setVisible(AndroidUtil.isJellyBeanMR1OrLater());
        findPreference("languages_download_list").setVisible(AndroidUtil.isHoneycombOrLater());
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
            case "enable_black_theme":
                ((PreferencesActivity) getActivity()).exitAndRescan();
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
