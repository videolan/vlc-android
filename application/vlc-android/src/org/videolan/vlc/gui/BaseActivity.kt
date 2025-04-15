package org.videolan.vlc.gui

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.BaseContextWrappingDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
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
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.RemoteAccessUtils
import org.videolan.vlc.viewmodels.DisplaySettingsViewModel


abstract class BaseActivity : AppCompatActivity() {

    private var startColor: Int = 0
    lateinit var settings: SharedPreferences
    private var lastDisplayedOTPCode = ""
    var windowLayoutInfo: WindowLayoutInfo? = null
    private val displaySettingsViewModel: DisplaySettingsViewModel by viewModels()

    open val displayTitle = false
    open fun forcedTheme():Int? = null
    open var isOTPActivity:Boolean = false

    /**
     * Enables edge-to-edge mode for this activity.
     * Set it to false if you want to override this behavior.
     */
    open var isEdgeToEdge = true
    abstract fun getSnackAnchorView(overAudioPlayer:Boolean = false): View?
    private var baseContextWrappingDelegate: AppCompatDelegate? = null
    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            FileUtils.getUri(result.data?.data)?.let { MediaUtils.openMediaNoUi(this, it) }
        }
    }

    /**
     * Triggered when a display setting is changed
     *
     * @param key the display settings key
     * @param value the new display settings value
     */
    open fun onDisplaySettingChanged(key:String, value:Any) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)
        if (settings.getString("app_theme", "-1") == "0") isEdgeToEdge = false
        if (isEdgeToEdge) enableEdgeToEdge()
        settings = Settings.getInstance(this)
        if (isEdgeToEdge) ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
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
        lifecycleScope.launch {
            //listen to display settings changes
            displaySettingsViewModel.settingChangeFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        onDisplaySettingChanged(it.key, it.value)
                        displaySettingsViewModel.consume()
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        Permissions.emptyCache()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (System.currentTimeMillis() -  Permissions.timeAsked < 300) {
                //Answered really quick (not human) -> forwarding to app settings
                Permissions.showAppSettingsPage(this)
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val relockItem = menu?.findItem(R.id.pin_relocked)
        if (relockItem != null) {
            relockItem.isVisible = Settings.safeMode && PinCodeDelegate.pinUnlocked.value == true
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
