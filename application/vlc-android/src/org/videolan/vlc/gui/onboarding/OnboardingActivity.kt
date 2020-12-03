package org.videolan.vlc.gui.onboarding

import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
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
import org.videolan.vlc.gui.helpers.hf.PermissionViewmodel
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.resumePermissionRequest
import org.videolan.vlc.gui.view.NonSwipeableViewPager
import org.videolan.vlc.util.Permissions

const val ONBOARDING_DONE_KEY = "app_onboarding_done"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class OnboardingActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, IOnScanningCustomizeChangedListener {

    private lateinit var viewPager: NonSwipeableViewPager

    private val indicators by lazy(LazyThreadSafetyMode.NONE) { arrayOf(
            findViewById<View>(R.id.indicator0),
            findViewById<View>(R.id.indicator1),
            findViewById<View>(R.id.indicator2),
            findViewById<View>(R.id.indicator3)
    ) }

    private val viewModel: OnboardingViewModel by viewModels()
    private val permissionModel: PermissionViewmodel by viewModels()

    private lateinit var onboardingPagerAdapter: OnboardingFragmentPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.onboarding_grey_dark)
        }

        viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
        setContentView(R.layout.activity_onboarding)
        viewPager = findViewById(R.id.pager)

        val count = viewModel.adapterCount

        onboardingPagerAdapter = OnboardingFragmentPagerAdapter(supportFragmentManager, count)
        viewPager.adapter = onboardingPagerAdapter
        viewPager.addOnPageChangeListener(this)
        viewPager.scrollEnabled = viewModel.permissionGranted

        selectPage(0)

        if (count == 4) onCustomizedChanged(true)

        if (permissionModel.permissionPending) lifecycleScope.launch {
            if (resumePermissionRequest()) viewPager.currentItem++
        }
    }

    fun onPrevious(@Suppress("UNUSED_PARAMETER") v: View) {
        if (viewPager.currentItem > 0) viewPager.currentItem--
    }

    fun onNext(@Suppress("UNUSED_PARAMETER") v: View) {
        lifecycleScope.launch {
            if (viewPager.currentItem == 0 && !viewModel.permissionGranted) {
                viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
                        || getStoragePermission()
                if (!viewModel.permissionGranted) {
                    return@launch
                } else {
                    viewPager.scrollEnabled = true
                }
            }
            if (viewPager.currentItem < viewPager.adapter!!.count) {
                viewPager.currentItem++
            }
        }
    }

    fun onDone(@Suppress("UNUSED_PARAMETER") v: View) {
        completeOnBoarding()
    }

    override fun onDestroy() {
        super.onDestroy()
        Permissions.sAlertDialog?.run { dismiss() }
    }

    private fun completeOnBoarding() {
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
        if (viewPager.currentItem != 0) super.onBackPressed()
    }

    private fun selectPage(index: Int) {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Selecting page $index pager nb of item: ${onboardingPagerAdapter.count}")
        //Navigation button states
        if (index == 0) {
            previous.animate().scaleY(0f).scaleX(0f).alpha(0f)
        } else {
            previous.animate().scaleY(1f).scaleX(1f).alpha(0.6f)
        }

        if (index == onboardingPagerAdapter.count - 1) {
            next.animate().scaleY(0f).scaleX(0f).alpha(0f)
            doneButton.animate().cancel()
            doneButton.visibility = View.VISIBLE
            doneButton.animate().scaleY(1f).scaleX(1f).alpha(1f).setListener(null)
        } else {
            next.animate().scaleY(1f).scaleX(1f).alpha(1f)
            doneButton.animate().scaleY(0f).scaleX(0f).alpha(0f).setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
                    doneButton.visibility = View.GONE
                }

                override fun onAnimationStart(animation: Animator?) {}

                override fun onAnimationCancel(animation: Animator?) {}
            })
        }

        //Indicator states
        for (pos in indicators.indices) {
            if (pos != index) {
                indicators[pos].animate()?.alpha(0.6f)?.scaleX(0.5f)?.scaleY(0.5f)
            } else {
                indicators[pos].animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
            }
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        selectPage(position)
    }

    override fun onCustomizedChanged(customizeEnabled: Boolean) {
        if (customizeEnabled) {
            indicators[3].visibility = View.VISIBLE
            indicators[3].animate()?.alpha(0.6f)?.scaleX(0.5f)?.scaleY(0.5f)!!.setListener(null)
            if (MediaParsingService.preselectedStorages.isEmpty()) lifecycleScope.launch {
                MediaParsingService.preselectedStorages.run {
                    addAll(AndroidDevices.externalStorageDirectories)
                    AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path?.let { add(it) }
                    AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DCIM_DIRECTORY_URI.path?.let { add(it) }
                    AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI.path?.let { add(it) }
                    AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI.path?.let { add(it) }
                    AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI.path?.let { add(it) }
                    AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI.path?.let { add(it) }
                }
            }
        } else {
            MediaParsingService.preselectedStorages.clear()
            indicators[3].animate()?.scaleY(0f)?.scaleX(0f)?.alpha(0f)?.setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
                    indicators[3].visibility = View.GONE
                }

                override fun onAnimationCancel(animation: Animator?) {}

                override fun onAnimationStart(animation: Animator?) {}

            })
        }
        onboardingPagerAdapter.onCustomizedChanged(customizeEnabled)
        viewModel.adapterCount = if (customizeEnabled) 4 else 3
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "New adapter count: ${viewModel.adapterCount}")
    }
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun Activity.startOnboarding() = startActivityForResult(Intent(this, OnboardingActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
