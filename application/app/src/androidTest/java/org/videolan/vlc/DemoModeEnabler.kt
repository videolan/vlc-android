/*
 * ************************************************************************
 *  DemoModeEnabler.kt
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

package org.videolan.vlc

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader

class DemoModeEnabler {

    fun enable() {
        executeShellCommand("settings put global sysui_demo_allowed 1")
        sendCommand("exit")
        sendCommand("enter")
        sendCommand("notifications", "visible" to "false")
        sendCommand("network", "wifi" to "show", "level" to "4", "fully" to "true")
        sendCommand("network", "mobile" to "show", "level" to "4", "datatype" to "4g")
        sendCommand("battery", "level" to "100", "plugged" to "true")
        sendCommand("clock", "hhmm" to "1000")
    }

    fun disable() {
        sendCommand("exit")
    }

    private fun sendCommand(command: String, vararg extras: Pair<String, Any>) {
        val exec = StringBuilder("am broadcast -a com.android.systemui.demo -e command $command")
        for ((key, value) in extras) {
            exec.append(" -e $key $value")
        }
        executeShellCommand(exec.toString())
    }

    private fun executeShellCommand(command: String) {
        waitForCompletion(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
    }

    private fun waitForCompletion(descriptor: ParcelFileDescriptor) {
        val reader = BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(descriptor)))
        reader.use {
            it.readText()
        }
    }
}