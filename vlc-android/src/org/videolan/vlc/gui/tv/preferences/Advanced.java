/*
 * *************************************************************************
 *  Advanced.java
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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;

import org.videolan.libvlc.util.HWDecoderUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class Advanced extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    protected int getXml() {
        return R.xml.preferences_adv;
    }


    @Override
    protected int getTitleId() {
        return R.string.advanced_prefs_category;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TextUtils.equals(BuildConfig.FLAVOR_target, "chrome")) {
            findPreference("quit_app").setEnabled(false);
        }

        // Audio output
        ListPreference aoutPref = (ListPreference) findPreference("aout");
        final HWDecoderUtil.AudioOutput aout = HWDecoderUtil.getAudioOutputFromDevice();
        if (aout == HWDecoderUtil.AudioOutput.AUDIOTRACK || aout == HWDecoderUtil.AudioOutput.OPENSLES) {
            /* no AudioOutput choice */
            findPreference("aout").setVisible(false);
        } else {
            int aoutEntriesId = R.array.aouts;
            int aoutEntriesIdValues = R.array.aouts_values;
            aoutPref.setEntries(aoutEntriesId);
            aoutPref.setEntryValues(aoutEntriesIdValues);
            final String value = aoutPref.getValue();
            if (value == null)
                aoutPref.setValue(String.valueOf(VLCOptions.AOUT_AUDIOTRACK));
            else {
                /* number of entries decreased, handle old values */
                final int intValue = Integer.parseInt(value);
                if (intValue != VLCOptions.AOUT_AUDIOTRACK && intValue != VLCOptions.AOUT_OPENSLES)
                    aoutPref.setValue(String.valueOf(VLCOptions.AOUT_AUDIOTRACK));
            }
        }
        // Video output
//        FIXME : This setting is disable until OpenGL is fixed
//        ListPreference voutPref = (ListPreference) findPreference("vout");
//        int voutEntriesId = LibVlcUtil.isGingerbreadOrLater() ? R.array.vouts : R.array.vouts_froyo;
//        int voutEntriesIdValues = LibVlcUtil.isGingerbreadOrLater() ? R.array.vouts_values : R.array.vouts_values_froyo;
//        voutPref.setEntries(voutEntriesId);
//        voutPref.setEntryValues(voutEntriesIdValues);
//        if (voutPref.getValue() == null)
//            voutPref.setValue("0"  VOUT_ANDROID_SURFACE );
        // Network caching

        EditTextPreference networkCachingPref = (EditTextPreference) findPreference("network_caching");
        networkCachingPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = sharedPrefs.edit();
                try {
                    editor.putInt("network_caching_value", Integer.parseInt((String)newValue));
                } catch(NumberFormatException e) {
                    editor.putInt("network_caching_value", 0);
                    editor.putString("network_caching", "0");
                }
                Util.commitPreferences(editor);
                return true;
            }
        });

        // Set locale
        EditTextPreference setLocalePref = (EditTextPreference) findPreference("set_locale");
        setLocalePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Toast.makeText(getActivity(), R.string.set_locale_popup, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
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
        switch (preference.getKey()) {
            case "clear_history":
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.clear_history)
                        .setMessage(R.string.validation)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                MediaDatabase db = MediaDatabase.getInstance();
                                db.clearSearchHistory();
                            }
                        })

                        .setNegativeButton(android.R.string.cancel, null).show();
                return true;
            case "clear_media_db":
                MediaDatabase.getInstance().emptyDatabase();
                BitmapCache.getInstance().clear();
                AudioUtil.clearCacheFolders();
                getActivity().setResult(PreferencesActivity.RESULT_RESCAN);
                Toast.makeText(getActivity(), R.string.media_db_cleared, Toast.LENGTH_SHORT).show();
                return true;
            case "quit_app":
                android.os.Process.killProcess(android.os.Process.myPid());
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key){
            case "aout":
            case "network_caching":
            case "vout":
                VLCInstance.restart();
                if (getActivity() != null )
                    ((PreferencesActivity)getActivity()).restartMediaPlayer();
        }
    }
}
