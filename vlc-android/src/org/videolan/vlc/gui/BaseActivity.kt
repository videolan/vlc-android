package org.videolan.vlc.gui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import org.videolan.tools.KeyHelper
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogHandler
import org.videolan.tools.Settings
import org.videolan.vlc.util.getContextWithLocale

abstract class BaseActivity : AppCompatActivity(), IDialogHandler {

    lateinit var settings: SharedPreferences
    private val dialogDelegate = DialogDelegate()

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        /* Theme must be applied before super.onCreate */
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale())
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale()
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
