/*
 * ************************************************************************
 *  OnRepeatListenerTouch.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.helpers

import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle

/**
 *
 * @param initialInterval Initial interval in millis
 * @param normalInterval Normal interval in millis
 * @param clickListener The OnClickListener to trigger
 */
class OnRepeatListenerTouch(private val initialInterval: Int, private val normalInterval: Int, speedUpDelay: Int, private val clickListener: View.OnClickListener, listenerLifecycle: Lifecycle) : View.OnTouchListener, OnRepeatListener(initialInterval, normalInterval, speedUpDelay, clickListener, listenerLifecycle) {
    /**
     *
     * @param clickListener The OnClickListener to trigger
     */
    constructor(clickListener: View.OnClickListener, listenerLifecycle: Lifecycle) : this(DEFAULT_INITIAL_DELAY, DEFAULT_NORMAL_DELAY, DEFAULT_SPEEDUP_DELAY, clickListener, listenerLifecycle)

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                startRepeating(view)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopRepeating(view)
                return true
            }
        }
        return false
    }
}
