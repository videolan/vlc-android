package org.videolan.vlc.gui.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import org.videolan.vlc.R

class VerticalSeekBar : AppCompatSeekBar {
    private var listener: OnSeekBarChangeListener? = null
    var fromUser = false

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {

        //The custom drawable looks not great for kitkat. So we use the default one to mitigate the issue
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.po_seekbar)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val handled = super.onTouchEvent(event)

        if (handled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    fromUser = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    fromUser = false
                }
            }
        }

        return handled
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        listener = l
        super.setOnSeekBarChangeListener(l)
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return false


            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                fromUser = true
                //to allow snaping to save current state when modifying a band from DPAD
                listener?.onStartTrackingTouch(this)

                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) -1 else 1
                var currentProgress = progress + (direction * keyProgressIncrement)

                if (currentProgress > max) {
                    currentProgress = max
                } else if (currentProgress < 0) {
                    currentProgress = 0
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setProgress(currentProgress, true) else progress = currentProgress

                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        fromUser = false
        return super.onKeyUp(keyCode, event)
    }


}