package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.slider.Slider
import kotlin.math.max

class VerticalSeekBarContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val childSlider: Slider?
        get() {
            val child = if (childCount > 0) getChildAt(0) else null
            return if (child is Slider) child else null
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {

        val seekBar = childSlider

        if (seekBar != null) {
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            seekBar.measure(
                    MeasureSpec.makeMeasureSpec(max(0, h - vPadding), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(max(0, w - hPadding), MeasureSpec.AT_MOST))
        }

        applyViewRotation(w, h)
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val slider = childSlider
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (slider != null && widthMode != MeasureSpec.EXACTLY) {
            val seekBarWidth: Int = slider.measuredHeight
            val seekBarHeight: Int = slider.measuredWidth
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val innerContentWidthMeasureSpec = MeasureSpec.makeMeasureSpec((widthSize - hPadding).coerceAtLeast(0), widthMode)
            val innerContentHeightMeasureSpec = MeasureSpec.makeMeasureSpec((heightSize - vPadding).coerceAtLeast(0), heightMode)

            slider.measure(innerContentHeightMeasureSpec, innerContentWidthMeasureSpec)

            val measuredWidth = View.resolveSizeAndState(seekBarWidth + hPadding, widthMeasureSpec, 0)
            val measuredHeight = View.resolveSizeAndState(seekBarHeight + vPadding, heightMeasureSpec, 0)

            setMeasuredDimension(measuredWidth, measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }


    private fun applyViewRotation(w: Int, h: Int) {
        val slider = childSlider
        layoutDirection = View.LAYOUT_DIRECTION_LTR

        if (slider != null) {
            val seekBarMeasuredWidth = slider.measuredWidth
            val seekBarMeasuredHeight = slider.measuredHeight
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val hOffset = (max(0, w - hPadding) - seekBarMeasuredHeight) / 2f
            val lp = slider.layoutParams

            lp.width = max(0, h - vPadding)
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT

            slider.layoutParams = lp

            slider.pivotX = 0f
            slider.pivotY = 0f

            slider.rotation = 270f
            slider.translationX = hOffset
            slider.translationY = seekBarMeasuredWidth.toFloat()
        }
    }

}