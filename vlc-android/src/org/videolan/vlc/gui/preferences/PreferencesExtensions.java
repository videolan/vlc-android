package org.videolan.vlc.gui.preferences;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.util.Log;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionsManager;

import java.util.ArrayList;
import java.util.List;

public class PreferencesExtensions extends BasePreferenceFragment {

    private List<ExtensionListing> mExtensions = new ArrayList<>();
    private SharedPreferences mSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplication());
        mExtensions = ExtensionsManager.getInstance().getExtensions(getActivity().getApplication(), false);
        if (mExtensions.isEmpty())
            createEmptyPref();
        else
            createCheckboxes();
    }

    @Override
    protected int getXml() {
        return R.xml.preferences_extensions;
    }

    @Override
    protected int getTitleId() {
        return R.string.extensions_prefs_category;
    }

    private void createCheckboxes() {
        PreferenceScreen preferenceScreen = this.getPreferenceScreen();

        PreferenceCategory preferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        preferenceCategory.setTitle(R.string.extensions_enable_category);
        preferenceScreen.addPreference(preferenceCategory);

        PackageManager pm = getActivity().getApplicationContext().getPackageManager();
        for (int i = 0 ; i < mExtensions.size() ; ++i) {
            ExtensionListing extension = mExtensions.get(i);
            CheckBoxPreference checkbox = new CheckBoxPreference(preferenceScreen.getContext());
            checkbox.setTitle(extension.title());
            checkbox.setSummary(extension.description());
            checkbox.setKey("extension_" + extension.componentName().getPackageName());
            int iconRes = extension.menuIcon();
            Drawable extensionIcon = null;
            if (iconRes != 0) {
                try {
                    Resources res = pm.getResourcesForApplication(extension.componentName().getPackageName());
                    extensionIcon = res.getDrawable(extension.menuIcon());
                } catch (PackageManager.NameNotFoundException e) {}
            }
            if (extensionIcon != null)
                checkbox.setIcon(extensionIcon);
            else
                try {
                    checkbox.setIcon(pm.getApplicationIcon(mExtensions.get(i).componentName().getPackageName()));
                } catch (PackageManager.NameNotFoundException e) {
                    checkbox.setIcon(R.drawable.icon);
                }

            preferenceCategory.addPreference(checkbox);
            checkbox.setChecked(mSettings.getBoolean("extension_" + extension.componentName().getPackageName(), false));
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;
        if (preference.getKey().startsWith("extension_")) {
            mSettings.edit()
                    .putBoolean(preference.getKey(), ((TwoStatePreference) preference).isChecked())
                    .apply();
        }
        return super.onPreferenceTreeClick(preference);
    }


    private void createEmptyPref() {
        PreferenceScreen preferenceScreen = this.getPreferenceScreen();
        PreferenceCategory preferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        preferenceCategory.setTitle(R.string.extensions_empty);
        preferenceScreen.addPreference(preferenceCategory);
    }
}
