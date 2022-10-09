package org.videolan.vlc.gui.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.EXTRA_FIRST_RUN
import org.videolan.resources.EXTRA_UPGRADE
import org.videolan.resources.PREF_FIRST_RUN
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.*
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
        super.onCreate(savedInstanceState)
//        viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
        setContentView(R.layout.activity_onboarding)
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
            putInt("fragment_id", if (viewModel.scanStorages) R.id.nav_video else R.id.nav_directories)
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

    private fun askPermission() {
        lifecycleScope.launch {
            val onlyMedia = viewModel.permissionType == PermissionType.MEDIA
            viewModel.permissionAlreadyAsked = true
            getStoragePermission(withDialog = false, onlyMedia = onlyMedia)
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
            FragmentName.ASK_PERMISSION -> if(viewModel.permissionType != PermissionType.NONE && !viewModel.permissionAlreadyAsked) askPermission() else showFragment(if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.NO_PERMISSION)
            FragmentName.NO_PERMISSION -> showFragment(if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.THEME)
            FragmentName.NOTIFICATION_PERMISSION -> if(!Permissions.canSendNotifications(applicationContext) && !viewModel.notificationPermissionAlreadyAsked) askNotificationPermission() else showFragment(FragmentName.THEME)
            FragmentName.SCAN -> if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S && !Permissions.canSendNotifications(applicationContext)) showFragment(FragmentName.NOTIFICATION_PERMISSION) else showFragment(FragmentName.THEME)
            else ->  onDone()
        }
        if (viewModel.currentFragment == FragmentName.THEME) nextButton.text = getString(R.string.done)
    }

    fun manageNextVisibility(visible: Boolean) {
        nextButton.visibility = if (visible) View.VISIBLE else View.GONE
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
