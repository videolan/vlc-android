package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.videolan.tools.Settings
import org.videolan.vlc.R

class HalfCircleView : View {
    private var isLeft: Boolean = true
    private val paint = Paint()

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttributes(attrs, 0)
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initAttributes(attrs, 0)
        initialize()
    }

    private fun initAttributes(attrs: AttributeSet, defStyle: Int) {
        attrs.let {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.HalfCircleView, 0, defStyle)
            try {
                isLeft = a.getBoolean(R.styleable.HalfCircleView_is_left, true)
            } catch (e: Exception) {
            } finally {
                a.recycle()
            }
        }
    }

    private fun initialize() {
        paint.color = ContextCompat.getColor(context, R.color.blacktransparent)
        paint.isAntiAlias = true
        if (Settings.showTvUi) {
            background = ContextCompat.getDrawable(context, if (isLeft) R.drawable.half_circle_tv_left else R.drawable.half_circle_tv_right)
        } else {
            background = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!Settings.showTvUi) {
            val cx = if (isLeft) -width else width * 2
            val cy = height / 2
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), width.toFloat()*2, paint)
        }
        super.onDraw(canvas)
    }
}