package org.videolan.vlc.gui;

import android.content.SharedPreferences;
import android.os.Bundle;

import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    protected SharedPreferences mSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mSettings = Settings.INSTANCE.getInstance(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    private void applyTheme() {
        if (AndroidDevices.showTvUi(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            setTheme(R.style.Theme_VLC_Black);
            return;
        }
        if (mSettings.contains("app_theme")) {
            AppCompatDelegate.setDefaultNightMode(Integer.valueOf(mSettings.getString("app_theme", "-1")));
        } else if (mSettings.contains("daynight") || mSettings.contains("enable_black_theme")) { // legacy support
            final boolean daynight = mSettings.getBoolean("daynight", false);
            final boolean dark = mSettings.getBoolean("enable_black_theme", false);
            final int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : daynight ? AppCompatDelegate.MODE_NIGHT_AUTO : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }
}
