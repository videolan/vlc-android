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

package org.videolan.vlc.gui.tv.preferences;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.Preference;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.util.VLCInstance;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesFragment extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String TAG = "VLC/PreferencesFragment";

    public final static String PLAYBACK_HISTORY = "playback_history";

    @Override
    protected int getXml() {
        return R.xml.preferences;
    }

    @Override
    protected int getTitleId() {
        return R.string.preferences;
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findPreference("screen_orientation").setVisible(false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equalsIgnoreCase("hardware_acceleration")) {
            VLCInstance.restart();
            if (getActivity() != null )
                ((PreferencesActivity)getActivity()).restartMediaPlayer();
        }
    }
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()){
            case "directories":
                Intent intent = new Intent(VLCApplication.getAppContext(), SecondaryActivity.class);
                intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER);
                startActivity(intent);
                getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                return true;
            case "ui_category":
                loadFragment(new PreferencesUi());
                break;
            case "video_category":
                loadFragment(new PreferencesVideo());
                break;
            case "subtitles_category":
                loadFragment(new PreferencesSubtitles());
                break;
            case "audio_category":
                loadFragment(new PreferencesAudio());
                break;
            case "adv_category":
                loadFragment(new PreferencesAdvanced());
                break;
            case PLAYBACK_HISTORY:
                getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                return true;
            default:
                return super.onPreferenceTreeClick(preference);
        }
        return true;
    }
}
