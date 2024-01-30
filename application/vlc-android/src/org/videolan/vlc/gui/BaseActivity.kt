package org.videolan.vlc.gui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.BaseContextWrappingDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.resources.AppContextProvider
import org.videolan.tools.KeyHelper
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.gui.helpers.hf.PinCodeDelegate
import org.videolan.vlc.gui.helpers.hf.checkPIN
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.RemoteAccessUtils


abstract class BaseActivity : AppCompatActivity() {

    private var currentNightMode: Int = 0
    private var startColor: Int = 0
    lateinit var settings: SharedPreferences
    private var lastDisplayedOTPCode = ""
    var windowLayoutInfo: WindowLayoutInfo? = null

    open val displayTitle = false
    open fun forcedTheme():Int? = null
    open var isOTPActivity:Boolean = false
    abstract fun getSnackAnchorView(overAudioPlayer:Boolean = false): View?
    private var baseContextWrappingDelegate: AppCompatDelegate? = null
    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            FileUtils.getUri(result.data?.data)?.let { MediaUtils.openMediaNoUi(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        /* Theme must be applied before super.onCreate */
        applyTheme()
        super.onCreate(savedInstanceState)
        if (UiTools.currentNightMode != resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            UiTools.invalidateBitmaps()
            UiTools.currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                WindowInfoTracker.getOrCreate(this@BaseActivity)
                        .windowLayoutInfo(this@BaseActivity)
                        .collect { layoutInfo ->
                            windowLayoutInfo = layoutInfo
                        }
            }
        }
        PinCodeDelegate.pinUnlocked.observe(this) {
            invalidateOptionsMenu()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                RemoteAccessUtils.otpFlow.collect {
                    if (!isOTPActivity && it != null && lastDisplayedOTPCode != it) {
                        lastDisplayedOTPCode = it
                        val i = Intent(this@BaseActivity, OTPCodeActivity::class.java)
                        i.flags = i.flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                        startActivity(i)
                    }
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val relockItem = menu?.findItem(R.id.pin_relocked)
        if (relockItem != null) {
            relockItem.isVisible = PinCodeDelegate.pinUnlocked.value == true
        }
        val unlockItem = menu?.findItem(R.id.pin_unlock)
        if (unlockItem != null) {
            unlockItem.isVisible = Settings.safeMode && PinCodeDelegate.pinUnlocked.value == false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.pin_relocked) {
            PinCodeDelegate.pinUnlocked.postValue(false)
            UiTools.snacker(this, R.string.safe_mode_enabled)
            return true
        }
        if (item.itemId == R.id.pin_unlock) {
            lifecycleScope.launch { checkPIN(true) }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun openFile(pickerInitialUri: Uri) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
            resultLauncher.launch(intent)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (displayTitle) {
            findViewById<View>(R.id.toolbar_icon).setGone()
            findViewById<View>(R.id.toolbar_vlc_title).setGone()
        }
    }

    override fun getDelegate() = baseContextWrappingDelegate
            ?: BaseContextWrappingDelegate(super.getDelegate()).apply { baseContextWrappingDelegate = this }

    override fun createConfigurationContext(overrideConfiguration: Configuration) = super.createConfigurationContext(overrideConfiguration).getContextWithLocale(AppContextProvider.locale)

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        return super.onKeyUp(keyCode, event)
    }

    override fun onSupportActionModeStarted(mode: androidx.appcompat.view.ActionMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startColor = window.statusBarColor
            val typedValue = TypedValue()
            theme.resolveAttribute(R.attr.actionModeBackground, typedValue, true)
            window.statusBarColor = typedValue.data
        }
        super.onSupportActionModeStarted(mode)
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) window.statusBarColor = startColor
        super.onSupportActionModeFinished(mode)
    }
}
