package org.videolan.vlc.gui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Settings

open class BaseActivity : AppCompatActivity() {

    protected lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        /* Theme must be applied before super.onCreate */
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applyTheme() {
        if (AndroidDevices.showTvUi(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            setTheme(R.style.Theme_VLC_Black)
            return
        }
        if (settings.contains("app_theme")) {
            AppCompatDelegate.setDefaultNightMode(Integer.valueOf(settings.getString("app_theme", "-1")!!))
        } else if (settings.contains("daynight") || settings.contains("enable_black_theme")) { // legacy support
            val daynight = settings.getBoolean("daynight", false)
            val dark = settings.getBoolean("enable_black_theme", false)
            val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else if (daynight) AppCompatDelegate.MODE_NIGHT_AUTO else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
