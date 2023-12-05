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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnRepeat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.webserver.R

class RemoteAccessOnboardingHowFragment : RemoteAccessOnboardingFragment() {
    private lateinit var vizu: MiniVisualizer
    private lateinit var playPause: ImageView
    private lateinit var browserLink: View
    private lateinit var titleView: TextView
    private val animSet = AnimatorSet()
    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.remote_access_onboarding_how, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
        browserLink = view.findViewById(R.id.browser_link)
        playPause = view.findViewById(R.id.play_pause)
        vizu = view.findViewById(R.id.vizu)
        if (Settings.showTvUi) {
            view.findViewById<ImageView>(R.id.deviceImage).setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_tv))
            (vizu.layoutParams as ConstraintLayout.LayoutParams).bottomMargin = 12.dp
        }


        var iteration = 0
        browserLink.pivotX = 0F
        val slideHorizontalAnimator = ObjectAnimator.ofFloat(browserLink, View.SCALE_X, 0F, 1F)
        slideHorizontalAnimator.interpolator = AccelerateDecelerateInterpolator()
        slideHorizontalAnimator.duration = 500
        slideHorizontalAnimator.repeatCount = ValueAnimator.INFINITE

        val playPauseAnimator = ObjectAnimator.ofFloat(playPause, View.ALPHA, 0F, 1F)
        playPauseAnimator.interpolator = AccelerateDecelerateInterpolator()
        playPauseAnimator.duration = 500
        playPauseAnimator.doOnRepeat {

            when (iteration % 4) {
                0 -> {
                    vizu.start()
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_remote_access_onboarding_pause))
                }

                1 -> {
                    vizu.stop()
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_remote_access_onboarding_file))
                }

                2 -> {
                    browserLink.pivotX = browserLink.width.toFloat()

                }

                3 -> {
                    browserLink.pivotX = 0F
                    playPause.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_remote_access_onboarding_play))
                }
            }
            iteration++
            browserLink.setGone()
            playPause.setGone()
            lifecycleScope.launch(Dispatchers.Main) {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    animSet.cancel()
                    delay(1500L)
                    browserLink.setVisible()
                    playPause.setVisible()
                    animSet.start()
                }
            }
        }
        playPauseAnimator.repeatCount = ValueAnimator.INFINITE



        animSet.playTogether(slideHorizontalAnimator, playPauseAnimator)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            animSet.currentPlayTime = 1500L
//        }
    }

    override fun onResume() {
        super.onResume()
        animSet.start()
    }

    override fun onPause() {
        vizu.stop()
        animSet.childAnimations.forEach {
            it.removeAllListeners()
        }
        animSet.cancel()
        super.onPause()
    }

    companion object {
        fun newInstance(): RemoteAccessOnboardingHowFragment {
            return RemoteAccessOnboardingHowFragment()
        }
    }
}