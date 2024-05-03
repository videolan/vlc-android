package org.videolan.vlc.gui.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import org.videolan.vlc.R

class MiniVisualizer : LinearLayout {
    private lateinit var stopSet: AnimatorSet
    private lateinit var animator: AnimatorSet
    private lateinit var visualizers: Array<View>
    private lateinit var visualizer1: View
    private lateinit var visualizer2: View
    private lateinit var visualizer3: View
    private var mainColor: Int = Color.BLACK

    constructor(context: Context) : super(context) {
        initViews()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttributes(attrs, 0)
        initViews()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initAttributes(attrs, defStyle)
        initViews()
    }

    private fun initAttributes(attrs: AttributeSet, defStyle: Int) {
        attrs.let {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.MiniVisualizer, 0, defStyle)
            try {
                mainColor = a.getInt(R.styleable.MiniVisualizer_bar_color, R.color.black)
            } catch (e: Exception) {
                Log.w("", e.message, e)
            } finally {
                a.recycle()
            }
        }
    }

    private fun initViews() {

        LayoutInflater.from(context).inflate(R.layout.view_mini_visualizer, this, true)
        visualizer1 = findViewById(R.id.visualizer1)
        visualizer2 = findViewById(R.id.visualizer2)
        visualizer3 = findViewById(R.id.visualizer3)
        visualizers = arrayOf(visualizer1, visualizer2, visualizer3)
        visualizers.forEach {
            it.setBackgroundColor(mainColor)
            it.scaleY = 0.1f
            it.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (it.height > 0) {
                        it.pivotY = it.height.toFloat()
                        it.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
        }
    }

    fun start() {
        if (!::animator.isInitialized) {
            val scaleVisu1 = ObjectAnimator.ofFloat(visualizer1, "scaleY", 0.7f, 0.3f, 0.9f, 0.7f, 0.7f, 0.7f, 0.4f, 0.6f, 0.8f, 0.6f, 0.3f, 0.2f, 0.1f, 0.9f, 0.1f, 0.5f, 0.2f, 0.3f, 0.1f, 0.7f, 0.6f, 0.5f, 0.8f, 0.3f, 0.8f, 0.1f)
            scaleVisu1.repeatCount = ValueAnimator.INFINITE
            val scaleVisu2 = ObjectAnimator.ofFloat(visualizer2, "scaleY", 0.2f, 0.8f, 0.7f, 0.8f, 0.8f, 0.3f, 0.5f, 0.4f, 0.8f, 0.3f, 0.7f, 0.9f, 0.5f, 0.8f, 0.1f, 0.3f, 0.2f, 0.5f, 0.2f, 0.7f, 0.3f, 0.4f, 0.1f, 0.5f, 0.7f, 0.2f)
            scaleVisu2.repeatCount = ValueAnimator.INFINITE
            val scaleVisu3 = ObjectAnimator.ofFloat(visualizer3, "scaleY", 0.3f, 0.1f, 0.3f, 0.3f, 0.3f, 0.7f, 0.7f, 0.9f, 0.3f, 0.7f, 0.0f, 0.9f, 0.3f, 0.2f, 0.4f, 0.8f, 0.5f, 1.0f, 0.2f, 0.4f, 1.0f, 0.3f, 0.2f, 0.5f, 0.7f, 0.5f)
            scaleVisu3.repeatCount = ValueAnimator.INFINITE

            animator = AnimatorSet()
            animator.playTogether(scaleVisu1, scaleVisu2, scaleVisu3)
            animator.duration = 3000
            animator.interpolator = LinearInterpolator()
            animator.start()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!animator.isStarted) {
                animator.start()
            }
        } else {
            if (animator.isPaused) {
                animator.resume()
            }
        }
    }

    fun stop() {
        if (::animator.isInitialized && animator.isRunning && animator.isStarted) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                animator.end()
            } else {
                animator.pause()
            }
        }

        if (!::stopSet.isInitialized) {
            val scaleY1 = ObjectAnimator.ofFloat(visualizer1, "scaleY", 0.1f)
            val scaleY2 = ObjectAnimator.ofFloat(visualizer2, "scaleY", 0.1f)
            val scaleY3 = ObjectAnimator.ofFloat(visualizer3, "scaleY", 0.1f)
            stopSet = AnimatorSet()
            stopSet.playTogether(scaleY3, scaleY2, scaleY1)
            stopSet.duration = 400
            stopSet.start()
        } else if (!stopSet.isStarted) {
            stopSet.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

}