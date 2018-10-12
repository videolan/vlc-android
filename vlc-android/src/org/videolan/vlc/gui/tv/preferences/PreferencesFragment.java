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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Permissions;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;

import static org.videolan.vlc.gui.preferences.PreferencesActivity.KEY_VIDEO_APP_SWITCH;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class PreferencesFragment extends BasePreferenceFragment {

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
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findPreference("screen_orientation").setVisible(false);
        findPreference("extensions_category").setVisible(false);
        findPreference("casting_category").setVisible(false);
        findPreference(KEY_VIDEO_APP_SWITCH).setVisible(AndroidDevices.hasPiP);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final Context context = getActivity();
        if (context == null) return false;
        switch (preference.getKey()){
            case "directories":
                if (VLCApplication.getMLInstance().isWorking())
                    Toast.makeText(context, getString(R.string.settings_ml_block_scan), Toast.LENGTH_SHORT).show();
                else if (Permissions.canReadStorage(context)) {
                    final Intent intent = new Intent(context.getApplicationContext(), SecondaryActivity.class);
                    intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER);
                    startActivity(intent);
                    getActivity().setResult(PreferencesActivity.RESULT_RESTART);
                } else Permissions.showStoragePermissionDialog((FragmentActivity) getActivity(), false);
                return true;
            default:
                return super.onPreferenceTreeClick(preference);
        }
    }
}
