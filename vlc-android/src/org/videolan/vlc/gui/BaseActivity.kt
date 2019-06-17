package org.videolan.vlc.gui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.videolan.vlc.gui.helpers.KeyHelper
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.util.Settings

open class BaseActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    init {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        /* Theme must be applied before super.onCreate */
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        return super.onKeyUp(keyCode, event)
    }

}
