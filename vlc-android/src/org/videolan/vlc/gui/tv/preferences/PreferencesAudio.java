/*
 * *************************************************************************
 *  PreferencesAudio.java
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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.HWDecoderUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.util.VLCInstance;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesAudio extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected int getXml() {
        return R.xml.preferences_audio;
    }

    @Override
    protected int getTitleId() {
        return R.string.audio_prefs_category;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findPreference("enable_headset_detection").setVisible(false);
        findPreference("enable_play_on_headset_insertion").setVisible(false);
        findPreference("headset_prefs_category").setVisible(false);
        findPreference("lockscreen_cover").setVisible(false);
        findPreference("audio_ducking").setVisible(!AndroidUtil.isOOrLater);

        final HWDecoderUtil.AudioOutput aout = HWDecoderUtil.getAudioOutputFromDevice();
        if (aout != HWDecoderUtil.AudioOutput.ALL) {
            /* no AudioOutput choice */
            findPreference("aout").setVisible(false);
        }
        updatePassThroughSummary();
        final boolean opensles = "1".equals(getPreferenceManager().getSharedPreferences().getString("aout", "0"));
        if (opensles) findPreference("audio_digital_output").setVisible(false);
    }

    private void updatePassThroughSummary() {
        final boolean pt = getPreferenceManager().getSharedPreferences().getBoolean("audio_digital_output", false);
        findPreference("audio_digital_output").setSummary(pt ? R.string.audio_digital_output_enabled : R.string.audio_digital_output_disabled);
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key){
            case "aout":
                VLCInstance.restart();
                if (getActivity() != null ) ((PreferencesActivity)getActivity()).restartMediaPlayer();
                final boolean opensles = "1".equals(getPreferenceManager().getSharedPreferences().getString("aout", "0"));
                if (opensles) ((CheckBoxPreference)findPreference("audio_digital_output")).setChecked(false);
                findPreference("audio_digital_output").setVisible(!opensles);
        }
    }
}