package org.videolan.vlc.gui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.videolan.tools.KeyHelper
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.util.Settings

open class BaseActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!localeSet) {
            UiTools.setLocale(this)
            localeSet = true
        }
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

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    companion object {
        var localeSet = false
    }
}
