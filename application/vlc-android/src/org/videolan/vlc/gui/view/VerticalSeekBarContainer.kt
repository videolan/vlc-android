package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.max

class VerticalSeekBarContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val childSeekBar: VerticalSeekBar?
        get() {
            val child = if (childCount > 0) getChildAt(0) else null
            return if (child is VerticalSeekBar) child else null
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {

        val seekBar = childSeekBar

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
        val seekBar = childSeekBar
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (seekBar != null && widthMode != MeasureSpec.EXACTLY) {
            val seekBarWidth: Int = seekBar.measuredHeight
            val seekBarHeight: Int = seekBar.measuredWidth
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val innerContentWidthMeasureSpec = MeasureSpec.makeMeasureSpec((widthSize - hPadding).coerceAtLeast(0), widthMode)
            val innerContentHeightMeasureSpec = MeasureSpec.makeMeasureSpec((heightSize - vPadding).coerceAtLeast(0), heightMode)

            seekBar.measure(innerContentHeightMeasureSpec, innerContentWidthMeasureSpec)

            val measuredWidth = View.resolveSizeAndState(seekBarWidth + hPadding, widthMeasureSpec, 0)
            val measuredHeight = View.resolveSizeAndState(seekBarHeight + vPadding, heightMeasureSpec, 0)

            setMeasuredDimension(measuredWidth, measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }


    private fun applyViewRotation(w: Int, h: Int) {
        val seekBar = childSeekBar
        layoutDirection = View.LAYOUT_DIRECTION_LTR

        if (seekBar != null) {
            val seekBarMeasuredWidth = seekBar.measuredWidth
            val seekBarMeasuredHeight = seekBar.measuredHeight
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val hOffset = (max(0, w - hPadding) - seekBarMeasuredHeight) / 2f
            val lp = seekBar.layoutParams

            lp.width = max(0, h - vPadding)
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT

            seekBar.layoutParams = lp

            seekBar.pivotX = 0f
            seekBar.pivotY = 0f

            seekBar.rotation = 270f
            seekBar.translationX = hOffset
            seekBar.translationY = seekBarMeasuredWidth.toFloat()
        }
    }

}