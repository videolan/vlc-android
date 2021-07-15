package org.videolan.vlc.gui.onboarding

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import kotlinx.android.synthetic.main.onboarding_scanning.*
import org.videolan.resources.KEY_ANIMATED
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity

class OnboardingScanningFragment : OnboardingFragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val preferences by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(requireActivity()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_scanning, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        scanningEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.putSingle(KEY_MEDIALIBRARY_SCAN, if (isChecked) ML_SCAN_ON else ML_SCAN_OFF)
            viewModel.scanStorages = isChecked
            customizeButton.isEnabled = isChecked
        }
        nextButton.setOnClickListener {
            onboardingFragmentListener.onNext()
        }
        customizeButton.setOnClickListener {
            val activity = requireActivity()

            val intent = Intent(activity.applicationContext, SecondaryActivity::class.java)
            intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER_ONBOARDING)
            intent.putExtra(KEY_ANIMATED, true)
            requireActivity().startActivity(intent)
            activity.overridePendingTransition( R.anim.slide_in_bottom, R.anim.no_animation )
        }
    }

    companion object {
        fun newInstance() = OnboardingScanningFragment()
    }
}

