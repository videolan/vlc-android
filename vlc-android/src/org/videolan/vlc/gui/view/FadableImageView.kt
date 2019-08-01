package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class FadableImageView : AppCompatImageView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    private fun fade() {
        alpha = 0f
        animate().cancel()
        animate().alpha(1f)
    }

    fun resetFade() {
        animate().cancel()
        post {
            alpha = 1f
        }
    }

    private fun launchAnim(hasToFade: Boolean) {
        if (!hasToFade) resetFade() else fade()
    }

    override fun setBackground(background: Drawable?) {
        super.setBackground(background)
        launchAnim(background != null)
    }

    override fun setBackgroundResource(resid: Int) {
        super.setBackgroundResource(resid)
        launchAnim(resid != 0)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        launchAnim(bm != null)
        super.setImageBitmap(bm)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        launchAnim(drawable != null)
        super.setImageDrawable(drawable)
    }

    override fun setImageIcon(icon: Icon?) {
        launchAnim(icon != null)
        super.setImageIcon(icon)
    }

    override fun setImageResource(resId: Int) {
        launchAnim(resId != 0)
        super.setImageResource(resId)
    }

    override fun setImageURI(uri: Uri?) {
        launchAnim(uri != null)
        super.setImageURI(uri)
    }
}