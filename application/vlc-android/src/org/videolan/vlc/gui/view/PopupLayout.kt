/*
 * ************************************************************************
 *  PopupView.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.core.view.GestureDetectorCompat
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.tools.CUSTOM_POPUP_HEIGHT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R

class PopupLayout : ConstraintLayout, ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private lateinit var screenSize: DisplayMetrics
    private var vlcVout: IVLCVout? = null
    private var windowManager: WindowManager? = null
    private var gestureDetector: GestureDetectorCompat? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var scaleFactor = 1.0
    private var popupWidth: Int = 0
    private var popupHeight: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0.toFloat()
    private var initialTouchY: Float = 0.toFloat()
    private lateinit var mLayoutParams: WindowManager.LayoutParams

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    fun setVLCVOut(vout: IVLCVout) {
        vlcVout = vout
        vlcVout!!.setWindowSize(popupWidth, popupHeight)
    }

    /*
     * Remove layout from window manager
     */
    fun close() {
        keepScreenOn = false
        windowManager!!.removeView(this)
        windowManager = null
        vlcVout = null
    }

    fun setGestureDetector(gdc: GestureDetectorCompat) {
        gestureDetector = gdc
    }

    /*
     * Update layout dimensions and apply layout params to window manager
     */
    fun setViewSize(requestedWidth: Int, requestedHeight: Int) {
        var width = requestedWidth
        var height = requestedHeight
        if (width > screenWidth) {
            height = height * screenWidth / width
            width = screenWidth
        }
        if (height > screenHeight) {
            width = width * screenHeight / height
            height = screenHeight
        }
        containInScreen(width, height)
        mLayoutParams.width = width
        mLayoutParams.height = height
        windowManager!!.updateViewLayout(this, mLayoutParams)
        if (vlcVout != null)
            vlcVout!!.setWindowSize(popupWidth, popupHeight)
    }

    private fun init(context: Context) {
        windowManager = context.applicationContext.getSystemService()

        screenSize = DisplayMetrics().also { windowManager!!.defaultDisplay.getMetrics(it) }
        popupWidth = context.resources.getDimensionPixelSize(R.dimen.video_pip_width)
        popupHeight = context.resources.getDimensionPixelSize(R.dimen.video_pip_height)
        val ratio = popupWidth.toFloat() / popupHeight.toFloat()
        val customPopupHeight = Settings.getInstance(context).getInt(CUSTOM_POPUP_HEIGHT, -1)
        if (customPopupHeight != -1) {
            popupHeight = customPopupHeight
            popupWidth = (popupHeight.toFloat() * ratio).toInt()
        }

        val params = WindowManager.LayoutParams(
                popupWidth,
                popupHeight,
                if (AndroidUtil.isOOrLater) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE)

        params.gravity = Gravity.BOTTOM or Gravity.START
        params.x = 50
        params.y = 50
        scaleGestureDetector = ScaleGestureDetector(context, this)
        setOnTouchListener(this)
        windowManager!!.addView(this, params)
        if (!isInEditMode) {
            mLayoutParams = layoutParams as WindowManager.LayoutParams
        }

        updateWindowSize()
    }

    private fun updateWindowSize() {
        val size = Point()
        windowManager!!.defaultDisplay.getSize(size)
        screenWidth = size.x
        screenHeight = size.y
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (windowManager == null)
            return false
        if (scaleGestureDetector != null)
            scaleGestureDetector!!.onTouchEvent(event)
        if (gestureDetector != null && gestureDetector!!.onTouchEvent(event))
            return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = mLayoutParams.x
                initialY = mLayoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                updateWindowSize()
                return true
            }
            MotionEvent.ACTION_UP -> return true
            MotionEvent.ACTION_MOVE -> if (scaleGestureDetector == null || !scaleGestureDetector!!.isInProgress) {
                mLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                mLayoutParams.y = initialY - (event.rawY - initialTouchY).toInt()
                containInScreen(mLayoutParams.width, mLayoutParams.height)
                windowManager!!.updateViewLayout(this@PopupLayout, mLayoutParams)
                return true
            }
        }
        return false
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor.toDouble()

        scaleFactor = scaleFactor.coerceIn(0.1, 5.0)
        popupWidth = (width * scaleFactor).toInt()
        popupHeight = (height * scaleFactor).toInt()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        setViewSize(popupWidth, popupHeight)
        Settings.getInstance(context).putSingle(CUSTOM_POPUP_HEIGHT, popupHeight)
        scaleFactor = 1.0
    }

    private fun containInScreen(width: Int, height: Int) {
        mLayoutParams.x = mLayoutParams.x.coerceAtLeast(0)
        mLayoutParams.y = mLayoutParams.y.coerceAtLeast(0)
        if (mLayoutParams.x + width > screenWidth)
            mLayoutParams.x = screenWidth - width
        if (mLayoutParams.y + height > screenHeight)
            mLayoutParams.y = screenHeight - height
    }

    companion object {
        private const val TAG = "VLC/PopupView"
    }
}
