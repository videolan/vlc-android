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

package org.videolan.vlc.gui.preferences;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;

import java.util.List;

public class PreferencesFragment extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String TAG = "VLC/PreferencesFragment";
    public final static int REQUEST_CODE_STORAGE_ACCES = 42;

    @Override
    protected int getXml() {
        return R.xml.preferences;
    }

    @Override
    protected int getTitleId() {
        return R.string.preferences;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!AndroidDevices.hasTsp()){
            findPreference("screen_orientation").setEnabled(false);
            findPreference("enable_black_theme").setEnabled(false);
        }

        // Writing to external sd card
        Preference extSdCardWritePref = findPreference("ext_sdcard_write");
        if (AndroidUtil.isLolliPopOrLater() && BuildConfig.DEBUG) {
            extSdCardWritePref.setSummary(getUriPermissions());
            extSdCardWritePref.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            startActivityForResult(intent, REQUEST_CODE_STORAGE_ACCES);
                            return true;
                        }
                    });
        }
        else {
            extSdCardWritePref.setVisible(false);
        }

        // Screen orientation
        ListPreference screenOrientationPref = (ListPreference) findPreference("screen_orientation");
        screenOrientationPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("screen_orientation_value", (String) newValue);
                Util.commitPreferences(editor);
                return true;
            }
        });

        /*** SharedPreferences Listener to apply changes ***/
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String getUriPermissions() {
        StringBuilder sb = new StringBuilder();
        Context context = getContext();
        List<UriPermission> persistedUriPermissions = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission uriPermission : persistedUriPermissions) {
            final DocumentFile file = DocumentFile.fromTreeUri(context, uriPermission.getUri());
            sb.append(uriPermission.getUri().getPath() + "\n");
        }
        return sb.toString();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_STORAGE_ACCES && AndroidUtil.isLolliPopOrLater()) {
            if (resultCode == Activity.RESULT_OK) {
                Context context = getContext();
                Uri treeUri = data.getData();
                final DocumentFile treeFile = DocumentFile.fromTreeUri(context, treeUri);
                ContentResolver contentResolver = context.getContentResolver();

                // revoke access if a permission already exists
                final List<UriPermission> persistedUriPermissions = contentResolver.getPersistedUriPermissions();
                for (UriPermission uriPermission : persistedUriPermissions) {
                    final DocumentFile file = DocumentFile.fromTreeUri(context, uriPermission.getUri());
                    if (treeFile.getName().equals(file.getName())) {
                        Log.d(TAG, "Revoking permission to " + treeFile);
                        contentResolver.releasePersistableUriPermission(uriPermission.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        findPreference("ext_sdcard_write").setSummary(getUriPermissions());
                        return;
                    }
                }

                // else set permission
                Log.d(TAG, "Taking permission to " + treeUri);
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                findPreference("ext_sdcard_write").setSummary(getUriPermissions());
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equalsIgnoreCase("hardware_acceleration")
                || key.equalsIgnoreCase("subtitle_text_encoding")) {
            VLCInstance.restart(getActivity());
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
            case "enable_black_theme":
                ((PreferencesActivity) getActivity()).exitAndRescan();
                return true;
            case "ui_category":
                loadFragment(new PreferencesUi());
                break;
            case "perf_category":
                loadFragment(new PreferencesPerformances());
                break;
            case "adv_category":
                loadFragment(new Advanced());
                break;
            case "dev_category":
                loadFragment(new Developer());
                break;
            default:
                return super.onPreferenceTreeClick(preference);
        }
        return true;
    }
}
