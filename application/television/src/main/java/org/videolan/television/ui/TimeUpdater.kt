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
package org.videolan.television.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.text.DateFormat
import java.util.*

private const val TAG = "VLC/TimeUpdater"

class TimeUpdater(private val activity: Activity, private val tv: TextView) : DefaultLifecycleObserver {

    private fun updateTime() {

        val format = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        tv.text = format.format(Date())
    }

    override fun onStart(owner: LifecycleOwner) {
        activity.registerReceiver(clockReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        updateTime()
    }

    override fun onStop(owner: LifecycleOwner) = activity.unregisterReceiver(clockReceiver)

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTime()
        }
    }
}

fun FragmentActivity.registerTimeView(tv: TextView?) = tv?.let { lifecycle.addObserver(TimeUpdater(this, it)) }