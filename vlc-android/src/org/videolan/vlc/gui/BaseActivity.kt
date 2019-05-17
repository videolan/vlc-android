package org.videolan.vlc.gui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.util.Settings

open class BaseActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        /* Theme must be applied before super.onCreate */
        applyTheme()
        super.onCreate(savedInstanceState)
    }
}
