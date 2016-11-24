/*
 * *************************************************************************
 *  PreferencesVideo.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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

import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesVideo extends BasePreferenceFragment {

    @Override
    protected int getXml() {
        return R.xml.preferences_video;
    }

    @Override
    protected int getTitleId() {
        return R.string.video_prefs_category;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findPreference("force_list_portrait").setVisible(false);
        findPreference("save_brightness").setVisible(false);
        findPreference("video_min_group_length").setVisible(false);
        findPreference("enable_volume_gesture").setVisible(AndroidDevices.hasTsp());
        findPreference("enable_brightness_gesture").setVisible(AndroidDevices.hasTsp());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;
        switch (preference.getKey()){
            case "video_min_group_length":
                getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
