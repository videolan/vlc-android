package org.videolan.vlc.webserver.gui.remoteaccess.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.webserver.viewmodels.RemoteAccessOnboardingViewModel


class RemoteAccessOnboardingActivity : AppCompatActivity(), OnboardingFragmentListener {
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private val viewModel: RemoteAccessOnboardingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        showFragment(viewModel.currentFragment)
    }

    fun showFragment(fragmentName:FragmentName, backward:Boolean = false) {
        val fragment = supportFragmentManager.getFragment(Bundle(), fragmentName.name) ?:
        when (fragmentName) {
            FragmentName.WELCOME -> RemoteAccessOnboardingWelcomeFragment.newInstance()
            FragmentName.HOW -> RemoteAccessOnboardingHowFragment.newInstance()
            FragmentName.SSL -> RemoteAccessOnboardingSslFragment.newInstance()
            FragmentName.OTP -> RemoteAccessOnboardingOtpFragment.newInstance()
            FragmentName.CONTENT -> RemoteAccessOnboardingContentFragment.newInstance()
        }
        (fragment as RemoteAccessOnboardingFragment).onboardingFragmentListener = this
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
        skipButton = findViewById(R.id.skip_button)
        skipButton.setOnClickListener { onDone() }
        nextButton = findViewById(R.id.next_button)
        nextButton.setOnClickListener { onNext() }
    }


    override fun onDone() {
        finish()
    }

    override fun onNext() {
        when(viewModel.currentFragment) {
            FragmentName.WELCOME -> showFragment(FragmentName.HOW)
            FragmentName.HOW -> showFragment(FragmentName.SSL)
            FragmentName.SSL -> showFragment(FragmentName.OTP)
            FragmentName.OTP -> showFragment(FragmentName.CONTENT)
            else ->  onDone()
        }
        if (viewModel.currentFragment == FragmentName.CONTENT) {
            nextButton.text = getString(R.string.done)
            skipButton.setGone()
        }
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
    CONTENT
}