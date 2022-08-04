package org.videolan.vlc.gui.view

import android.animation.Animator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import java.util.concurrent.atomic.AtomicBoolean

class FadableImageView : AppCompatImageView {
    //FIXME This field is sometimes null, despite a correct initialization order
    // and non-nullable declaration
    private var animationRunning : AtomicBoolean? = AtomicBoolean(false)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    private fun fade() {
        if (animationRunning?.get() == true) return
        alpha = 0f
        animationRunning?.set(true)
        animate().setListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(p0: Animator) {
            }

            override fun onAnimationEnd(p0: Animator) {
                animationRunning?.set(false)
                alpha = 1f
            }

            override fun onAnimationCancel(p0: Animator) {
                animationRunning?.set(false)
                alpha = 1f
            }

            override fun onAnimationStart(p0: Animator) {
            }
        }).alpha(1f)
    }

    fun resetFade() {
        post {
            animate().cancel()
            alpha = 1f
        }
    }

    override fun setBackground(background: Drawable?) {
        super.setBackground(background)
        if (background == null || background is ColorDrawable) resetFade() else fade()
    }

    override fun setBackgroundResource(resid: Int) {
        super.setBackgroundResource(resid)
        if (resid != 0) fade() else resetFade()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        if (bm != null) fade() else resetFade()
        super.setImageBitmap(bm)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (drawable != null) fade() else resetFade()
        super.setImageDrawable(drawable)
    }

    override fun setImageIcon(icon: Icon?) {
        if (icon != null) fade() else resetFade()
        super.setImageIcon(icon)
    }

    override fun setImageResource(resId: Int) {
        if (resId != 0) fade() else resetFade()
        super.setImageResource(resId)
    }

    override fun setImageURI(uri: Uri?) {
        if (uri != null) fade() else resetFade()
        super.setImageURI(uri)
    }
}