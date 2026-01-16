/*
 * ************************************************************************
 *  DefaultTvActivity.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.util.startMedialibrary
import org.videolan.television.util.EventThrottler
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager

open class DefaultTvActivity : AppCompatActivity(), IDialogManager {
    private val horizontalThrottler = EventThrottler(75L)
    private val verticalThrottler = EventThrottler(100L)
    private val dialogsDelegate = DialogDelegate()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val isThrottled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                horizontalThrottler.throttleEvent()
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                verticalThrottler.throttleEvent()
            }

            else -> false
        }

        return isThrottled || super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Medialibrary.getInstance().isStarted) startMedialibrary(firstRun = false, upgrade = false, parse = true)
        dialogsDelegate.observeDialogs(this, this)
    }

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, this, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) { }
}