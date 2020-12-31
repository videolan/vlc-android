package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.videolan.vlc.R

class OnboardingFoldersFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title: TextView = view.findViewById(R.id.onboardingFolderTitle)
        val ariane: View = view.findViewById(R.id.ariane)
        ariane.viewTreeObserver.addOnGlobalLayoutListener {
            when (ariane.visibility) {
                View.VISIBLE -> title.visibility = View.GONE
                else -> title.visibility = View.VISIBLE
            }
        }
    }


    companion object {
        fun newInstance(): OnboardingFoldersFragment {
            return OnboardingFoldersFragment()
        }
    }
}