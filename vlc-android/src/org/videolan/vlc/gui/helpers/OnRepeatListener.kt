/*
 * *************************************************************************
 *  OnRepeatListener.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.helpers

import android.os.Message
import android.view.MotionEvent
import android.view.View

import org.videolan.vlc.util.WeakHandler

/**
 *
 * @param initialInterval Initial interval in millis
 * @param normalInterval Normal interval in millis
 * @param clickListener The OnClickListener to trigger
 */
class OnRepeatListener(private val initialInterval: Int, private val normalInterval: Int, private val clickListener: View.OnClickListener) : View.OnTouchListener {
    private var downView: View? = null

    private val handler = OnRepeatHandler(this)

    init {
        if (initialInterval < 0 || normalInterval < 0)
            throw IllegalArgumentException("negative interval")
    }

    /**
     *
     * @param clickListener The OnClickListener to trigger
     */
    constructor(clickListener: View.OnClickListener) : this(DEFAULT_INITIAL_DELAY, DEFAULT_NORMAL_DELAY, clickListener) {}

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeMessages(ACTION_ONCLICK)
                handler.sendEmptyMessageDelayed(ACTION_ONCLICK, initialInterval.toLong())
                downView = view
                clickListener.onClick(view)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeMessages(ACTION_ONCLICK)
                downView = null
                return true
            }
        }
        return false
    }

    private class OnRepeatHandler(owner: OnRepeatListener) : WeakHandler<OnRepeatListener>(owner) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_ONCLICK -> {
                    sendEmptyMessageDelayed(ACTION_ONCLICK, owner!!.normalInterval.toLong())
                    owner!!.clickListener.onClick(owner!!.downView)
                }
            }
        }
    }

    companion object {

        private const val ACTION_ONCLICK = 0

        //Default values in milliseconds
        private const val DEFAULT_INITIAL_DELAY = 500
        private const val DEFAULT_NORMAL_DELAY = 150
    }
}
