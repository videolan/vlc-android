/*
 * ************************************************************************
 *  DpadHelper.kt
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

package org.videolan.vlc.util

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice

object DpadHelper {
    private val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    fun pressDPad(direction: Direction?, nbTimes: Int = 1) {
        for (i in 0 until nbTimes) {
            when (direction) {
                Direction.DOWN -> device.pressDPadDown()
                Direction.LEFT -> device.pressDPadLeft()
                Direction.UP -> device.pressDPadUp()
                Direction.RIGHT -> device.pressDPadRight()
                else -> {}
            }
            if (i < nbTimes - 1) SystemClock.sleep(300)
        }
    }

    fun pressHome() = device.pressHome()
    fun pressPip() = device.pressKeyCode(KeyEvent.KEYCODE_WINDOW)
    fun pressStop() = device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_STOP)
    fun pressBack() = device.pressBack()
    fun pressDPadCenter() = device.pressDPadCenter()
}