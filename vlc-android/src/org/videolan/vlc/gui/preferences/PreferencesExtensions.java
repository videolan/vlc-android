package org.videolan.vlc.gui.preferences;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.google.android.material.appbar.AppBarLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.appcompat.widget.SwitchCompat;
import android.view.View;

import org.videolan.vlc.R;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.gui.view.ClickableSwitchPreference;
import org.videolan.vlc.util.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PreferencesExtensions extends BasePreferenceFragment {

    private List<ExtensionListing> mExtensions = new ArrayList<>();
    private SharedPreferences mSettings;
    private PreferenceScreen preferenceScreen;
    private int count = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = Settings.INSTANCE.getInstance(requireContext());
        mExtensions = ExtensionsManager.getInstance().getExtensions(getActivity().getApplication(), false);
        preferenceScreen = this.getPreferenceScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppBarLayout) ((PreferencesActivity)getActivity()).findViewById(R.id.appbar)).setExpanded(true, false);
        createCheckboxes();
    }

    @Override
    public void onStop() {
        super.onStop();
        preferenceScreen.removeAll();
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
        PackageManager pm = getActivity().getApplicationContext().getPackageManager();
        for (int i = 0 ; i < mExtensions.size() ; ++i) {
            ExtensionListing extension = mExtensions.get(i);
            ClickableSwitchPreference switchPreference = new ClickableSwitchPreference(preferenceScreen.getContext());
            switchPreference.setTitle(extension.title());
            switchPreference.setSummary(extension.description());
            final String key = ExtensionsManager.EXTENSION_PREFIX + "_" + extension.componentName().getPackageName();
            switchPreference.setKey(key);
            int iconRes = extension.menuIcon();
            Drawable extensionIcon = null;
            if (iconRes != 0) {
                try {
                    Resources res = pm.getResourcesForApplication(extension.componentName().getPackageName());
                    extensionIcon = res.getDrawable(extension.menuIcon());
                } catch (PackageManager.NameNotFoundException e) {}
            }
            if (extensionIcon != null)
                switchPreference.setIcon(extensionIcon);
            else
                try {
                    switchPreference.setIcon(pm.getApplicationIcon(mExtensions.get(i).componentName().getPackageName()));
                } catch (PackageManager.NameNotFoundException e) {
                    switchPreference.setIcon(R.drawable.icon);
                }
            final boolean checked = mSettings.getBoolean(key, false);
            switchPreference.setChecked(checked);
            preferenceScreen.addPreference(switchPreference);
            switchPreference.setOnSwitchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((SwitchCompat)view).isChecked())
                        mSettings.edit().putBoolean(key, true).apply();
                    else
                        for (Map.Entry<String, ?> entry : mSettings.getAll().entrySet())
                            if (entry.getKey().startsWith(ExtensionsManager.EXTENSION_PREFIX + "_"))
                                mSettings.edit().putBoolean(entry.getKey(), false).apply();
                }
            });
            count++;
        }

        if (count == 0) {
            PreferenceCategory emptyCategory = new PreferenceCategory(preferenceScreen.getContext());
            emptyCategory.setTitle(R.string.extensions_empty);
            preferenceScreen.addPreference(emptyCategory);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key == null || !key.startsWith(ExtensionsManager.EXTENSION_PREFIX + "_"))
            return false;
        PreferencesExtensionFragment fragment = new PreferencesExtensionFragment();
        Bundle extras = new Bundle();
        extras.putString("extension_key", key);
        fragment.setArguments(extras);
        loadFragment(fragment);
        return super.onPreferenceTreeClick(preference);
    }
}
