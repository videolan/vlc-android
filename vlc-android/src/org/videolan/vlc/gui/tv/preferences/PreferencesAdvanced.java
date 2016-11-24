/*
 * *************************************************************************
 *  PreferencesAdvanced.java
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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.widget.Toast;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DebugLogActivity;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.VLCInstance;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesAdvanced extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
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
        findPreference("debug_logs").setVisible(AndroidUtil.isJellyBeanOrLater() ||
                (BuildConfig.DEBUG && getActivity().checkCallingOrSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED));

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
        if (preference.getKey() == null)
            return false;
        switch (preference.getKey()){
            case "debug_logs":
                Intent intent = new Intent(VLCApplication.getAppContext(), DebugLogActivity.class);
                startActivity(intent);
                return true;
            case "clear_history":
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                VLCApplication.getMLInstance().clearHistory();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
                return true;
            case "clear_media_db":
                VLCApplication.getMLInstance().reload();
                BitmapCache.getInstance().clear();
                AudioUtil.clearCacheFolders();
                Toast.makeText(VLCApplication.getAppContext(), R.string.media_db_cleared, Toast.LENGTH_SHORT).show();
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
            case "network_caching":
                SharedPreferences.Editor editor = sharedPreferences.edit();
                try {
                    editor.putInt("network_caching_value", Integer.parseInt(sharedPreferences.getString(key,"0")));
                } catch(NumberFormatException e) {
                    editor.putInt("network_caching_value", 0);
                    EditTextPreference networkCachingPref = (EditTextPreference) findPreference(key);
                    networkCachingPref.setText("");
                    UiTools.snacker(getView(), R.string.network_caching_popup);
                }
                editor.apply();
                // No break because need VLCInstance.restart();
            case "vout":
            case "chroma_format":
            case "deblocking":
            case "enable_frame_skip":
            case "enable_time_stretching_audio":
            case "enable_verbose_mode":
                VLCInstance.restart();
                if (getActivity() != null )
                    ((PreferencesActivity)getActivity()).restartMediaPlayer();
                break;
        }
    }
}
