package org.videolan.vlc.webserver.gui.webserver.onboarding

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.webserver.R


class WebserverOnboardingContentFragment : WebserverOnboardingFragment() {
    private lateinit var titleView: TextView
    private lateinit var vizu: MiniVisualizer
    private lateinit var filesLL: LinearLayout
    private lateinit var playbackLL: LinearLayout
    private lateinit var medialibraryLL: LinearLayout

    val disappearSet = AnimatorSet()
    val animationLoop = AnimatorSet()

    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.webserver_onboarding_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
        filesLL = view.findViewById(R.id.files)
        playbackLL = view.findViewById(R.id.playback)
        medialibraryLL = view.findViewById(R.id.medialibrary)
        vizu = view.findViewById(R.id.vizu)

        vizu.start()


        val appearingSets = ArrayList<AnimatorSet>()
        val disappearAnimations = ArrayList<ObjectAnimator>()
        arrayOf(medialibraryLL, filesLL, playbackLL).forEach {
            val bumpAnimationX = ObjectAnimator.ofFloat(it, View.SCALE_X, 0.75F, 1F)
            bumpAnimationX.interpolator = OvershootInterpolator()
            bumpAnimationX.duration = 1000

            val bumpAnimationY = ObjectAnimator.ofFloat(it, View.SCALE_Y, 0.75F, 1F)
            bumpAnimationY.interpolator = OvershootInterpolator()
            bumpAnimationY.duration = 1000

            val alphaAnimation = ObjectAnimator.ofFloat(it, View.ALPHA, 0F, 1F)
            alphaAnimation.interpolator = AccelerateDecelerateInterpolator()
            alphaAnimation.duration = 1000

            val alphaDisappearAnimation = ObjectAnimator.ofFloat(it, View.ALPHA, 1F, 0F)
            alphaDisappearAnimation.interpolator = AccelerateDecelerateInterpolator()
            alphaDisappearAnimation.duration = 1000
            disappearAnimations.add(alphaDisappearAnimation)

            val set = AnimatorSet()
            set.playTogether(bumpAnimationX, bumpAnimationY, alphaAnimation)
            appearingSets.add(set)
        }




        disappearSet.playTogether(disappearAnimations.toSet())
        disappearSet.doOnEnd {
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(2000)
                    animationLoop.start()
                }
            }
        }

        animationLoop.playSequentially(appearingSets.toMutableList() as List<Animator>?)
        animationLoop.doOnEnd {
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(2000)
                    disappearSet.start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        animationLoop.start()
    }

    override fun onPause() {
        super.onPause()
        vizu.stop()
        disappearSet.cancel()
        animationLoop.cancel()
    }

    companion object {
        fun newInstance(): WebserverOnboardingContentFragment {
            return WebserverOnboardingContentFragment()
        }
    }
}