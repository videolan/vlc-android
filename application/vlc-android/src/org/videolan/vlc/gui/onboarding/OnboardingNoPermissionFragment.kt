package org.videolan.vlc.gui.onboarding

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import org.videolan.vlc.R

class OnboardingNoPermissionFragment : OnboardingFragment() {
    private lateinit var titleView: TextView
    private val viewModel: OnboardingViewModel by activityViewModels()
    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_no_permission, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleView = view.findViewById(R.id.no_permission_title)
        view.findViewById<Button>(R.id.grant_permission_button).setOnClickListener {
            viewModel.permissionAlreadyAsked = false
            (requireActivity() as OnboardingActivity).showFragment(FragmentName.ASK_PERMISSION, true)
        }
    }

    companion object {
        fun newInstance(): OnboardingNoPermissionFragment {
            return OnboardingNoPermissionFragment()
        }
    }
}