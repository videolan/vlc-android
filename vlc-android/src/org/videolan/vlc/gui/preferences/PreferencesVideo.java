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

package org.videolan.vlc.gui.preferences;

import android.os.Bundle;
import android.support.v7.preference.Preference;
import org.videolan.vlc.R;

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
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;
        switch (preference.getKey()){
            case "video_min_group_length":
                getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                return true;
            case "force_list_portrait":
                ((PreferencesActivity) getActivity()).setRestart();
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
