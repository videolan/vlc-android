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

    override fun setBackground(background: Drawable?) {
        super.setBackground(background)
        if (background != null) fade() else resetFade()
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