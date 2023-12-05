package org.videolan.vlc.webserver.gui.remoteaccess.onboarding

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.vlc.webserver.R
import org.videolan.vlc.webserver.RemoteAccessOTP


class RemoteAccessOnboardingOtpFragment : RemoteAccessOnboardingFragment() {
    private lateinit var access: ImageView
    private lateinit var browserLink: View
    private lateinit var titleView: TextView
    private lateinit var deviceOTP: TextView
    private lateinit var browserOTP: TextView

    private val animSet = AnimatorSet()
    private lateinit var anims: List<Animator>

    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.remote_access_onboarding_otp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
        browserLink = view.findViewById(R.id.browser_link)
        deviceOTP = view.findViewById(R.id.otpDevice)
        browserOTP = view.findViewById(R.id.otpBrowser)
        access = view.findViewById(R.id.access)
        if (Settings.showTvUi)  view.findViewById<ImageView>(R.id.deviceImage).setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_tv))

        deviceOTP.text = RemoteAccessOTP.generateCode()

        browserLink.pivotX = 0F
        val slideHorizontalAnimator = ObjectAnimator.ofFloat(browserLink, View.SCALE_X, 0F, 1F)
        slideHorizontalAnimator.interpolator = AccelerateDecelerateInterpolator()
        slideHorizontalAnimator.duration = 1000

        val deviceOTPAnimator = ObjectAnimator.ofFloat(deviceOTP, View.ALPHA, 0F, 1F)
        deviceOTPAnimator.interpolator = AccelerateDecelerateInterpolator()
        deviceOTPAnimator.startDelay = 300
        deviceOTPAnimator.duration = 300

        val accessAnimator = ObjectAnimator.ofFloat(access, View.ALPHA, 0F, 1F)
        accessAnimator.interpolator = AccelerateDecelerateInterpolator()
        accessAnimator.duration = 500

        val accessTransAnimator = ObjectAnimator.ofFloat(access, View.TRANSLATION_Y, 16.dp.toFloat(), 0F)
        accessTransAnimator.interpolator = AccelerateDecelerateInterpolator()
        accessTransAnimator.duration = 500


        val orange = ContextCompat.getColor(requireActivity(), R.color.orange500)
        val red = ContextCompat.getColor(requireActivity(), R.color.red)
        val green = ContextCompat.getColor(requireActivity(), R.color.green400)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), orange, red)
        colorAnimation.duration = 500

        colorAnimation.addUpdateListener { animator -> browserLink.setBackgroundColor(animator.animatedValue as Int) }

        val accessAnims = AnimatorSet()
        accessAnims.playTogether(accessAnimator, accessTransAnimator, colorAnimation)
        var iteration = 0

        accessAnimator.doOnEnd {
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(2000)
                    access.alpha = 0F
                    browserLink.setBackgroundColor(orange)
                    iteration++
                    animSet.start()
                }
            }

        }


        animSet.doOnEnd {
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(400)
                    val text = if (iteration % 2 == 0) deviceOTP.text else RemoteAccessOTP.generateCode()
                    browserOTP.text = "${text.subSequence(0, 1)}   "
                    delay(400)
                    browserOTP.text = "*${text.subSequence(1, 2)}  "
                    delay(400)
                    browserOTP.text = "**${text.subSequence(2, 3)} "
                    delay(400)
                    browserOTP.text = "***${text.subSequence(3, 4)}"
                    delay(400)
                    browserOTP.text = "****"
                    delay(400)
                    browserOTP.text = ""

                    access.setImageDrawable(ContextCompat.getDrawable(requireActivity(), if (iteration % 2 == 0) R.drawable.ic_remote_access_onboarding_verified else R.drawable.ic_remote_access_onboarding_denied))
                    colorAnimation.setIntValues(orange, if (iteration % 2 == 0) green else red)
                    accessAnims.start()
                }
            }

        }
        anims = listOf(animSet, accessAnims)

        animSet.playSequentially(slideHorizontalAnimator, deviceOTPAnimator)


    }

    override fun onResume() {
        super.onResume()
         animSet.start()
   }

    override fun onPause() {
        super.onPause()
        anims.forEach { it.cancel() }
    }

    companion object {
        fun newInstance(): RemoteAccessOnboardingOtpFragment {
            return RemoteAccessOnboardingOtpFragment()
        }
    }
}