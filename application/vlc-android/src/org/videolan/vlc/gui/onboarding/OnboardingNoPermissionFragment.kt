package org.videolan.vlc.gui.onboarding

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission

class OnboardingNoPermissionFragment : OnboardingFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_no_permission, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.grant_permission_button).setOnClickListener {
            requireActivity().askStoragePermission(false, null)
        }
        view.findViewById<Button>(R.id.next_button).setOnClickListener {
            onboardingFragmentListener.onNext()
        }
    }

    companion object {
        fun newInstance(): OnboardingNoPermissionFragment {
            return OnboardingNoPermissionFragment()
        }
    }
}