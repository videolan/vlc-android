package org.videolan.vlc.gui;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Settings;

public class BaseActivity extends AppCompatActivity {

    static {
        AppCompatDelegate.setDefaultNightMode(VLCApplication.getAppContext() != null && Settings.INSTANCE.getInstance(VLCApplication.getAppContext()).getBoolean("daynight", false) ? AppCompatDelegate.MODE_NIGHT_AUTO : AppCompatDelegate.MODE_NIGHT_NO);
    }

    protected SharedPreferences mSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        /* Get settings */
        mSettings = Settings.INSTANCE.getInstance(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    private void applyTheme() {
        boolean enableBlackTheme = mSettings.getBoolean("enable_black_theme", false);
        if (AndroidDevices.showTvUi(this) || enableBlackTheme) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }
}
