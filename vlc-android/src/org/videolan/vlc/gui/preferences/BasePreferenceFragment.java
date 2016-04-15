/*
 * *************************************************************************
 *  BasePreferenceFragment.java
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
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.preferences.hack.MultiSelectListPreferenceDialogFragmentCompat;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {

    private static final String DIALOG_FRAGMENT_TAG = "android.support.v7.preference.PreferenceFragment.DIALOG";

    protected abstract int getXml();
    protected abstract int getTitleId();

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(getXml());
    }

    @Override
    public void onStart() {
        super.onStart();
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(getString(getTitleId()));
        }
    }

    protected void loadFragment(Fragment fragment) {
        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_placeholder, fragment)
                .addToBackStack("main")
                .commit();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (AndroidUtil.isHoneycombOrLater() && preference instanceof MultiSelectListPreference) {
            DialogFragment dialogFragment = MultiSelectListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
