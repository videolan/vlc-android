package org.videolan.vlc.webserver.gui.remoteaccess.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnRepeat
import androidx.core.content.ContextCompat
import org.videolan.tools.Settings
import org.videolan.vlc.webserver.R
import java.security.SecureRandom

class RemoteAccessOnboardingSslFragment : RemoteAccessOnboardingFragment() {
    private lateinit var browserLink: View
    private lateinit var titleView: TextView
    private lateinit var data: TextView
    private val animSet = AnimatorSet()
    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.remote_access_onboarding_ssl, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
        browserLink = view.findViewById(R.id.browser_link)
        data = view.findViewById(R.id.data)
        if (Settings.showTvUi)  view.findViewById<ImageView>(R.id.deviceImage).setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_tv))

        var iteration = 0
        browserLink.pivotX = 0F
        val slideHorizontalAnimator = ObjectAnimator.ofFloat(browserLink, View.SCALE_X, 0F, 1F)
        slideHorizontalAnimator.interpolator = AccelerateDecelerateInterpolator()
        slideHorizontalAnimator.duration = 2000
        slideHorizontalAnimator.repeatCount = ValueAnimator.INFINITE

        var last = 0L
        slideHorizontalAnimator.addUpdateListener {
            if (System.currentTimeMillis() - last > 150) {
                generateRandomData()
                last = System.currentTimeMillis()
            }
        }
        slideHorizontalAnimator.doOnRepeat {
            when (iteration % 2) {
                0 -> {
                    browserLink.pivotX = browserLink.width.toFloat()
                }

                1 -> {
                    browserLink.pivotX = 0F
                }

            }
            iteration++
        }
        animSet.playTogether(slideHorizontalAnimator)
    }

    private fun generateRandomData() {
        data.text = buildString {
            for (i in 0..7) {
                append(if (SecureRandom().nextBoolean()) "1" else "0")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        animSet.start()
    }

    override fun onPause() {
        animSet.cancel()
        super.onPause()
    }

    companion object {
        fun newInstance(): RemoteAccessOnboardingSslFragment {
            return RemoteAccessOnboardingSslFragment()
        }
    }
}