package org.videolan.vlc.gui.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_onboarding.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.resources.*
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.util.Permissions

const val ONBOARDING_DONE_KEY = "app_onboarding_done"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class OnboardingActivity : AppCompatActivity(), OnboardingFragmentListener {
    private val viewModel: OnboardingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
        setContentView(R.layout.activity_onboarding)
        showFragment(viewModel.currentFragment)
    }

    fun showFragment(fragmentName:FragmentName) {
        val fragment = supportFragmentManager.getFragment(Bundle(), fragmentName.name) ?:
        when (fragmentName) {
            FragmentName.WELCOME -> OnboardingWelcomeFragment.newInstance()
            FragmentName.ASK_PERMISSION -> OnboardingPermissionFragment.newInstance()
            FragmentName.SCAN -> OnboardingScanningFragment.newInstance()
            FragmentName.NO_PERMISSION -> OnboardingNoPermissionFragment.newInstance()
            FragmentName.THEME -> OnboardingThemeFragment.newInstance()
        }
        (fragment as OnboardingFragment).onboardingFragmentListener = this
        supportFragmentManager.commit {
            setCustomAnimations(
                 R.anim.anim_enter_right,
                 R.anim.anim_leave_left,
                 android.R.anim.fade_in,
                 android.R.anim.fade_out
            )
            replace(R.id.fragment_onboarding_placeholder, fragment, fragmentName.name)
        }
        viewModel.currentFragment = fragmentName
        close.setOnClickListener { onDone() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
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

    override fun onBackPressed() {
//        if (viewPager.currentItem != 0) super.onBackPressed()
    }

    override fun askPermission() {
        lifecycleScope.launch {
            getStoragePermission()
            onNext()
        }
    }

    override fun onNext() {
        when(viewModel.currentFragment) {
            FragmentName.WELCOME -> showFragment(FragmentName.ASK_PERMISSION)
            FragmentName.ASK_PERMISSION -> showFragment(if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.NO_PERMISSION)
            FragmentName.NO_PERMISSION -> showFragment(if (Permissions.canReadStorage(applicationContext)) FragmentName.SCAN else FragmentName.THEME)
            FragmentName.SCAN -> showFragment(FragmentName.THEME)
        }
    }

}

enum class FragmentName {
    WELCOME,
    ASK_PERMISSION,
    SCAN,
    NO_PERMISSION,
    THEME
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun Activity.startOnboarding() = startActivityForResult(Intent(this, OnboardingActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
