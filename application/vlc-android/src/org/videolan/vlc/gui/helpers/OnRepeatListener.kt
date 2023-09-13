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

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.SchedulerCallback

/**
 *
 * @param initialInterval Initial interval in millis
 * @param normalInterval Normal interval in millis
 * @param clickListener The OnClickListener to trigger
 * @param listenerLifecycle The lifecycle to attach to
 */
open class OnRepeatListener(private val initialInterval: Int, private val normalInterval: Int, private val speedUpDelay: Int, private val clickListener: View.OnClickListener, private val listenerLifecycle:Lifecycle): SchedulerCallback {
    var downView: View? = null
    var initialTime: Long = -1L
    @Suppress("LeakingThis")
    var scheduler: LifecycleAwareScheduler = LifecycleAwareScheduler(this)

    init {
        if (initialInterval < 0 || normalInterval < 0)
            throw IllegalArgumentException("negative interval")
    }



    fun startRepeating(view: View) {
        scheduler.cancelAction(ACTION_ONCLICK)
        scheduler.scheduleAction(ACTION_ONCLICK, initialInterval.toLong())
        downView = view
        initialTime = System.currentTimeMillis()
        clickListener.onClick(view)
        view.isPressed = true
        if (BuildConfig.DEBUG) Log.d("Delay", "onTouch: ACTION_DOWN")
    }

    fun stopRepeating(view: View) {
        scheduler.cancelAction(ACTION_ONCLICK)
        downView = null
        initialTime = -1L
        view.isPressed = false
        if (BuildConfig.DEBUG) Log.d("Delay", "onTouch: ACTION_UP")
    }

    companion object {

        private const val ACTION_ONCLICK = "action_onclick"

        //Default values in milliseconds
        const val DEFAULT_INITIAL_DELAY = 500
        const val DEFAULT_NORMAL_DELAY = 150
        const val DEFAULT_SPEEDUP_DELAY = 2000
    }

    override fun onTaskTriggered(id: String, data: Bundle) {
        when(id) {
            ACTION_ONCLICK -> {
                val interval = if (initialTime > -1L && System.currentTimeMillis() - initialTime > speedUpDelay) normalInterval.toLong() / 3 else normalInterval.toLong()
                scheduler.scheduleAction(ACTION_ONCLICK, interval)
                clickListener.onClick(downView)
            }
        }
    }

    override val lifecycle: Lifecycle
        get() = listenerLifecycle
}
