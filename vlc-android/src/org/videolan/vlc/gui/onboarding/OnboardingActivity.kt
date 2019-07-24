package org.videolan.vlc.gui.onboarding

import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.gui.view.NonSwipeableViewPager
import org.videolan.vlc.startMedialibrary
import org.videolan.vlc.util.*

const val ONBOARDING_DONE_KEY = "app_onboarding_done"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class OnboardingActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, IOnScanningCustomizeChangedListener, CoroutineScope by MainScope() {

    private lateinit var viewPager: NonSwipeableViewPager

    private val indicators: Array<View?> = arrayOfNulls(4)

    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var doneButton: Button
    lateinit var viewModel: OnboardingViewModel

    private lateinit var onboardingPagerAdapter: OnboardingFragmentPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.onboarding_grey_dark)
        }

        viewModel = getOnboardingModel()
        setContentView(R.layout.activity_onboarding)
        viewPager = findViewById(R.id.pager)
        indicators[0] = findViewById(R.id.indicator0)
        indicators[1] = findViewById(R.id.indicator1)
        indicators[2] = findViewById(R.id.indicator2)
        indicators[3] = findViewById(R.id.indicator3)

        previousButton = findViewById(R.id.previous)
        nextButton = findViewById(R.id.next)
        doneButton = findViewById(R.id.doneButton)



        previousButton.scaleX = 0f
        previousButton.scaleY = 0f
        previousButton.alpha = 0f

        nextButton.scaleX = 0f
        nextButton.scaleY = 0f
        nextButton.alpha = 0f


        doneButton.scaleX = 0f
        doneButton.scaleY = 0f
        doneButton.alpha = 0f
        doneButton.visibility = View.GONE

        indicators[3]!!.scaleX = 0f
        indicators[3]!!.scaleY = 0f
        indicators[3]!!.alpha = 0f
        indicators[3]!!.visibility = View.GONE


        previousButton.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = viewPager.currentItem - 1
            }
        }

        nextButton.setOnClickListener {
            launch {
                if (viewPager.currentItem == 0 && !viewModel.permissionGranted) {
                    viewModel.permissionGranted = Permissions.canReadStorage(applicationContext)
                            || StoragePermissionsDelegate.getStoragePermission(this@OnboardingActivity, false)
                    if (!viewModel.permissionGranted) {
                        return@launch
                    } else {
                        viewPager.scrollEnabled = true
                    }
                }
                if (viewPager.currentItem < viewPager.adapter!!.count) {
                    viewPager.currentItem = viewPager.currentItem + 1
                }
            }
        }

        doneButton.setOnClickListener { completeOnBoarding() }

        val count = viewModel.adapterCount

        onboardingPagerAdapter = OnboardingFragmentPagerAdapter(supportFragmentManager, count)
        viewPager.adapter = onboardingPagerAdapter
        viewPager.addOnPageChangeListener(this)
        viewPager.scrollEnabled = viewModel.permissionGranted

        selectPage(0)

        if (count == 4) onCustomizedChanged(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private fun completeOnBoarding() {
        setResult(RESULT_RESTART)
        Settings.getInstance(this)
                .edit()
                .putInt(PREF_FIRST_RUN, BuildConfig.VERSION_CODE)
                .putBoolean(ONBOARDING_DONE_KEY, true)
                .putInt(KEY_MEDIALIBRARY_SCAN, if (viewModel.scanStorages) ML_SCAN_ON else ML_SCAN_OFF)
                .putInt("fragment_id", if (viewModel.scanStorages) R.id.nav_video else R.id.nav_directories)
                .putString(KEY_APP_THEME, viewModel.theme.toString())
                .apply()
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
            previousButton.animate().scaleY(0f).scaleX(0f).alpha(0f)
        } else {
            previousButton.animate().scaleY(1f).scaleX(1f).alpha(0.6f)
        }

        if (index == onboardingPagerAdapter.count - 1) {
            nextButton.animate().scaleY(0f).scaleX(0f).alpha(0f)
            doneButton.animate().cancel()
            doneButton.visibility = View.VISIBLE
            doneButton.animate().scaleY(1f).scaleX(1f).alpha(1f).setListener(null)
        } else {
            nextButton.animate().scaleY(1f).scaleX(1f).alpha(1f)
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
        for (pos in 0 until indicators.size) {
            if (pos != index) {
                indicators[pos]?.animate()?.alpha(0.6f)?.scaleX(0.5f)?.scaleY(0.5f)
            } else {
                indicators[pos]?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
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
            indicators[3]?.visibility = View.VISIBLE
            indicators[3]?.animate()?.alpha(0.6f)?.scaleX(0.5f)?.scaleY(0.5f)!!.setListener(null)
            if (MediaParsingService.preselectedStorages.isEmpty()) launch {
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
            indicators[3]?.animate()?.scaleY(0f)?.scaleX(0f)?.alpha(0f)?.setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
                    indicators[3]?.visibility = View.GONE
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

fun Activity.startOnboarding() = startActivityForResult(Intent(this, OnboardingActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
