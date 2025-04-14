package org.videolan.vlc.gui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXTRA_FIRST_RUN
import org.videolan.resources.EXTRA_UPGRADE
import org.videolan.resources.PREF_FIRST_RUN
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_FRAGMENT_ID
import org.videolan.tools.KEY_MEDIALIBRARY_SCAN
import org.videolan.tools.ML_SCAN_OFF
import org.videolan.tools.ML_SCAN_ON
import org.videolan.tools.NOTIFICATION_PERMISSION_ASKED
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.hf.NotificationDelegate.Companion.getNotificationPermission
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.util.Permissions


const val ONBOARDING_DONE_KEY = "app_onboarding_done"

class OnboardingActivity : AppCompatActivity(), OnboardingFragmentListener {
    private lateinit var nextButton: Button
    private val viewModel: OnboardingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        if (AndroidDevices.canUseSystemNightMode()) enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        super.onCreate(savedInstanceState)
//        viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
        setContentView(R.layout.activity_onboarding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, windowInsets ->
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
        showFragment(viewModel.currentFragment)
    }

    fun showFragment(fragmentName:FragmentName, backward:Boolean = false) {
        val fragment = supportFragmentManager.getFragment(Bundle(), fragmentName.name) ?:
        when (fragmentName) {
            FragmentName.WELCOME -> OnboardingWelcomeFragment.newInstance()
            FragmentName.ASK_PERMISSION -> OnboardingPermissionFragment.newInstance()
            FragmentName.SCAN -> OnboardingScanningFragment.newInstance()
            FragmentName.NO_PERMISSION -> OnboardingNoPermissionFragment.newInstance()
            FragmentName.NOTIFICATION_PERMISSION -> OnboardingNotificationPermissionFragment.newInstance()
            FragmentName.THEME -> OnboardingThemeFragment.newInstance()
        }
        (fragment as OnboardingFragment).onboardingFragmentListener = this
        supportFragmentManager.commit {
            if (!backward) setCustomAnimations(
                 R.anim.anim_enter_right,
                 R.anim.anim_leave_left,
                 android.R.anim.fade_in,
                 android.R.anim.fade_out
            ) else setCustomAnimations(
                    R.anim.anim_enter_left,
                    R.anim.anim_leave_right,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            )
            replace(R.id.fragment_onboarding_placeholder, fragment, fragmentName.name)
        }
        viewModel.currentFragment = fragmentName
        findViewById<View>(R.id.skip_button).setOnClickListener { onDone() }
        nextButton = findViewById(R.id.next_button)
        nextButton.setOnClickListener { onNext() }
    }

    override fun onDestroy() {
        super.onDestroy()
        Permissions.sAlertDialog?.run { dismiss() }
    }

    override fun onDone() {
        setResult(RESULT_RESTART)
        Settings.getInstance(this).edit {
            putInt(PREF_FIRST_RUN, BuildConfig.VLC_VERSION_CODE)
            putBoolean(ONBOARDING_DONE_KEY, true)
            putInt(KEY_MEDIALIBRARY_SCAN, if (viewModel.scanStorages) ML_SCAN_ON else ML_SCAN_OFF)
            putInt(KEY_FRAGMENT_ID, if (viewModel.scanStorages) R.id.nav_video else R.id.nav_directories)
            putString(KEY_APP_THEME, viewModel.theme.toString())
        }
        if (!viewModel.scanStorages) MediaParsingService.preselectedStorages.clear()
        startMedialibrary(firstRun = true, upgrade = true, parse = viewModel.scanStorages)
        val intent = Intent(this@OnboardingActivity, MainActivity::class.java)
            .putExtra(EXTRA_FIRST_RUN, true)
            .putExtra(EXTRA_UPGRADE, true)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.permissionAlreadyAsked = true
                onNext()
            }
        }
    }

    private fun askPermission() {
        lifecycleScope.launch {
            val onlyMedia = viewModel.permissionType == PermissionType.MEDIA
            viewModel.permissionAlreadyAsked = true
            if (onlyMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this@OnboardingActivity, arrayOf<String>(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )
                return@launch
            } else getStoragePermission(withDialog = false, onlyMedia = onlyMedia)
            onNext()
        }
    }

    private fun askNotificationPermission() {
        lifecycleScope.launch {
            viewModel.notificationPermissionAlreadyAsked = true
            getNotificationPermission()
            Settings.getInstance(this@OnboardingActivity).edit {
                putBoolean(NOTIFICATION_PERMISSION_ASKED, true)
            }
            onNext()
        }
    }

    override fun onNext() {
        when(viewModel.currentFragment) {
            FragmentName.WELCOME -> if (Permissions.canReadStorage(this)) showFragment(FragmentName.SCAN) else showFragment(FragmentName.ASK_PERMISSION)
            FragmentName.ASK_PERMISSION -> {
//                if (viewModel.permissionType == PermissionType.MEDIA && Permissions.isStoragePermissionIncomplete(
//                        this
//                    )
//                ) askPermission()
//                else
                    if (viewModel.permissionType != PermissionType.NONE && !viewModel.permissionAlreadyAsked) askPermission() else showFragment(
                    if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.NO_PERMISSION
                )
            }
            FragmentName.NO_PERMISSION -> showFragment(if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.THEME)
            FragmentName.NOTIFICATION_PERMISSION -> if(!Permissions.canSendNotifications(applicationContext) && !viewModel.notificationPermissionAlreadyAsked) askNotificationPermission() else showFragment(FragmentName.THEME)
            FragmentName.SCAN -> if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S && !Permissions.canSendNotifications(applicationContext)) showFragment(FragmentName.NOTIFICATION_PERMISSION) else showFragment(FragmentName.THEME)
            else ->  onDone()
        }
        if (viewModel.currentFragment == FragmentName.THEME) nextButton.text = getString(R.string.done)
    }

}

enum class FragmentName {
    WELCOME,
    ASK_PERMISSION,
    SCAN,
    NO_PERMISSION,
    NOTIFICATION_PERMISSION,
    THEME
}

fun Activity.startOnboarding() = startActivityForResult(Intent(this, OnboardingActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
