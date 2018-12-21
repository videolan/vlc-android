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

package org.videolan.vlc.gui.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DebugLogActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WorkersKt;

import java.io.File;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

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
        if (BuildConfig.DEBUG) findPreference("debug_logs").setVisible(false);
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
                final Intent intent = new Intent(requireContext(), DebugLogActivity.class);
                startActivity(intent);
                return true;
            case "clear_history":
                new AlertDialog.Builder(requireContext())
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
                final Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                i.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(i);
                return true;
            case "quit_app":
                android.os.Process.killProcess(android.os.Process.myPid());
                return true;
            case "dump_media_db":
                if (VLCApplication.getMLInstance().isWorking())
                    UiTools.snacker(getView(), getString(R.string.settings_ml_block_scan));
                else WorkersKt.runIO(new Runnable() {
                        @Override
                        public void run() {
                            final Runnable dump = new Runnable() {
                                @Override
                                public void run() {
                                    final File db = new File(requireContext().getDir("db", Context.MODE_PRIVATE)+ Medialibrary.VLC_MEDIA_DB_NAME);

                                    if (FileUtils.copyFile(db, new File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)))
                                        WorkersKt.runOnMainThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                final Context ctx = getContext();
                                                if (ctx != null) Toast.makeText(ctx, "Database dumped on internal storage root", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    else WorkersKt.runOnMainThread(new Runnable() {
                                        @Override
                                        public void run() {
                                                final Context ctx = getContext();
                                                if (ctx != null) Toast.makeText(ctx, "Failed to dumped database", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            };
                            if (Permissions.canWriteStorage()) dump.run();
                            else Permissions.askWriteStoragePermission(getActivity(), false, dump);
                        }
                    });
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
            case "opengl":
            case "chroma_format":
            case "custom_libvlc_options":
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
