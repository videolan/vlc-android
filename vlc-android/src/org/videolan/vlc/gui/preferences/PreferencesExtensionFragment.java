package org.videolan.vlc.gui.preferences;


import android.content.SharedPreferences;
import android.os.Bundle;
import com.google.android.material.appbar.AppBarLayout;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import org.videolan.vlc.R;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.util.Settings;

import java.util.ArrayList;
import java.util.List;

public class PreferencesExtensionFragment extends BasePreferenceFragment {

    private ExtensionListing mExtension;
    private int mExtensionId;
    private String mExtensionTitle;
    private String mExtensionKey;
    private String mExtensionPackageName;
    private SharedPreferences mSettings;
    private PreferenceScreen preferenceScreen;
    private boolean androidAutoAvailable = false;
    private List<Preference> preferences = new ArrayList<>();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mSettings = Settings.INSTANCE.getInstance(requireActivity());
        setHasOptionsMenu(true);
        if (bundle == null)
            bundle = getArguments();

        if (bundle != null) {
            mExtensionKey = bundle.getString("extension_key");
            mExtensionPackageName = mExtensionKey.replace(ExtensionsManager.EXTENSION_PREFIX + "_", "");
            mExtensionId = ExtensionsManager.getInstance().getExtensionId(mExtensionPackageName);
            mExtension = ExtensionsManager.getInstance().getExtensions(getActivity().getApplication(), false).get(mExtensionId);
            mExtensionTitle = mExtension.title();
            setTitle(mExtensionTitle);
            androidAutoAvailable = ExtensionsManager.androidAutoInstalled && mExtension.androidAutoEnabled();
            createCheckboxes();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("extension_key", mExtensionKey);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppBarLayout) ((PreferencesActivity)getActivity()).findViewById(R.id.appbar)).setExpanded(true, false);
    }

    @Override
    protected int getXml() {
        return R.xml.preferences_extension_page;
    }

    @Override
    protected int getTitleId() {
        return 0;
    }

    private void setTitle(String title){
        final PreferencesActivity activity = (PreferencesActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null)
            ((PreferencesActivity)getActivity()).getSupportActionBar().setTitle(title);
    }

    private void createCheckboxes() {
        preferenceScreen = this.getPreferenceScreen();

        //Main switch
        SwitchPreferenceCompat switchPreference = new SwitchPreferenceCompat(preferenceScreen.getContext());
        switchPreference.setTitle(preferenceScreen.getContext().getString(R.string.extension_prefs_activation_title).toUpperCase());
        switchPreference.setKey(mExtensionKey);
        switchPreference.setChecked(mSettings.getBoolean(mExtensionKey, false));
        switchPreference.setOnPreferenceChangeListener(null);
        preferenceScreen.addPreference(switchPreference);

        //Android-auto
        if (androidAutoAvailable) {
            CheckBoxPreference checkbox = new CheckBoxPreference(preferenceScreen.getContext());
            checkbox.setTitle(R.string.android_auto);
            String key = mExtensionKey + "_" + ExtensionsManager.ANDROID_AUTO_SUFFIX;
            checkbox.setKey(key);
            checkbox.setChecked(switchPreference.isChecked() && mSettings.getBoolean(key, false));
            checkbox.setEnabled(switchPreference.isChecked());
            preferences.add(checkbox);
            preferenceScreen.addPreference(checkbox);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key == null || !key.startsWith(mExtensionKey))
            return false;

        if (key.equals(mExtensionKey)) {
            SwitchPreferenceCompat switchPreference = (SwitchPreferenceCompat)preference;
            mSettings.edit().putBoolean(key, switchPreference.isChecked()).apply();
            if (switchPreference.isChecked()) {
                for (Preference checkbox : preferences)
                    checkbox.setEnabled(true);
            } else {
                for (Preference checkbox : preferences) {
                    ((CheckBoxPreference)checkbox).setChecked(false);
                    mSettings.edit().putBoolean(checkbox.getKey(), false).apply();
                    checkbox.setEnabled(false);
                }
            }
        } else if (key.endsWith("_" + ExtensionsManager.ANDROID_AUTO_SUFFIX)) {
            mSettings.edit()
                    .putBoolean(preference.getKey(), ((TwoStatePreference) preference).isChecked())
                    .apply();
        }
        return super.onPreferenceTreeClick(preference);
    }
}