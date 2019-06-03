package org.videolan.vlc.gui.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class VerticalSeekBar : AppCompatSeekBar {


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)


    override fun onTouchEvent(event: MotionEvent): Boolean {

        val handled = super.onTouchEvent(event)

        if (handled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return handled
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return false


            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {

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


}