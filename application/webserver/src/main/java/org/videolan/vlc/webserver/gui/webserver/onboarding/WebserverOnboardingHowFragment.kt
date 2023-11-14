package org.videolan.vlc.webserver.gui.webserver.onboarding

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
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.webserver.R

class WebserverOnboardingHowFragment : WebserverOnboardingFragment() {
    private lateinit var vizu: MiniVisualizer
    private lateinit var playPause: ImageView
    private lateinit var browserLink: View
    private lateinit var titleView: TextView
    private val animSet = AnimatorSet()
    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.webserver_onboarding_how, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
        browserLink = view.findViewById(R.id.browser_link)
        playPause = view.findViewById(R.id.play_pause)
        vizu = view.findViewById(R.id.vizu)


        var iteration = 0
        browserLink.pivotX = 0F
        val slideHorizontalAnimator = ObjectAnimator.ofFloat(browserLink, View.SCALE_X, 0F, 0F, 0F, 1F)
        slideHorizontalAnimator.interpolator = AccelerateDecelerateInterpolator()
        slideHorizontalAnimator.duration = 4000
        slideHorizontalAnimator.repeatCount = ValueAnimator.INFINITE

        val playPauseAnimator = ObjectAnimator.ofFloat(playPause, View.ALPHA, 0F, 0F, 0F, 1F)
        playPauseAnimator.interpolator = AccelerateDecelerateInterpolator()
        playPauseAnimator.duration = 4000
        playPauseAnimator.doOnRepeat {
            when (iteration % 4) {
                0 -> {
                    vizu.start()
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_webserver_onboarding_pause))
                }

                1 -> {
                    vizu.stop()
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_webserver_onboarding_file))
                }

                2 -> {
                    browserLink.pivotX = browserLink.width.toFloat()

                }
                3 -> {
                    browserLink.pivotX = 0F
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_webserver_onboarding_play))
                }
            }
            iteration++
        }
        playPauseAnimator.repeatCount = ValueAnimator.INFINITE



        animSet.playTogether(slideHorizontalAnimator, playPauseAnimator)
        animSet.start()

    }

    override fun onDestroy() {
        animSet.cancel()
        super.onDestroy()
    }

    companion object {
        fun newInstance(): WebserverOnboardingHowFragment {
            return WebserverOnboardingHowFragment()
        }
    }
}