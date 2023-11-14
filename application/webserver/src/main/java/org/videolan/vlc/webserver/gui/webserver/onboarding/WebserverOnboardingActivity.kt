package org.videolan.vlc.webserver.gui.webserver.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.videolan.tools.RESULT_RESTART
import org.videolan.vlc.R
import org.videolan.vlc.webserver.viewmodels.WebServerOnboardingViewModel


class WebserverOnboardingActivity : AppCompatActivity(), OnboardingFragmentListener {
    private lateinit var nextButton: Button
    private val viewModel: WebServerOnboardingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        showFragment(viewModel.currentFragment)
    }

    fun showFragment(fragmentName:FragmentName, backward:Boolean = false) {
        val fragment = supportFragmentManager.getFragment(Bundle(), fragmentName.name) ?:
        when (fragmentName) {
            FragmentName.WELCOME -> WebserverOnboardingWelcomeFragment.newInstance()
            FragmentName.HOW -> WebserverOnboardingHowFragment.newInstance()
            //todo
//            FragmentName.SSL -> OnboardingScanningFragment.newInstance()
//            FragmentName.OTP -> OnboardingNoPermissionFragment.newInstance()
//            FragmentName.PERMISSIONS -> OnboardingNotificationPermissionFragment.newInstance()
            else -> WebserverOnboardingWelcomeFragment.newInstance()
        }
        (fragment as WebserverOnboardingFragment).onboardingFragmentListener = this
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


    override fun onDone() {
        setResult(RESULT_RESTART)
        finish()
    }

    override fun onNext() {
        when(viewModel.currentFragment) {
            FragmentName.WELCOME -> showFragment(FragmentName.HOW)
            FragmentName.HOW -> showFragment(FragmentName.SSL)
            FragmentName.SSL -> showFragment(FragmentName.OTP)
            FragmentName.PERMISSIONS -> showFragment(FragmentName.PERMISSIONS)
            else ->  onDone()
        }
        if (viewModel.currentFragment == FragmentName.PERMISSIONS) nextButton.text = getString(R.string.done)
    }

    fun manageNextVisibility(visible: Boolean) {
        nextButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

}

enum class FragmentName {
    WELCOME,
    HOW,
    SSL,
    OTP,
    PERMISSIONS
}