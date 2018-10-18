/*****************************************************************************
 * TimeUpdater.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.fragment.app.FragmentActivity
import android.widget.TextView
import java.util.*

private const val TAG = "VLC/TimeUpdater"

class TimeUpdater(private val activity: Activity, private val tv: TextView) : LifecycleObserver {

    private fun updateTime() {
        val calendar = Calendar.getInstance()
        tv.text = String.format("%d:%02d", calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE])
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        activity.registerReceiver(clockReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        updateTime()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun unregister() = activity.unregisterReceiver(clockReceiver)

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTime()
        }
    }
}

fun androidx.fragment.app.FragmentActivity.registerTimeView(tv: TextView?) = tv?.let { lifecycle.addObserver(TimeUpdater(this, it)) }