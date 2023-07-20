/*
 * ************************************************************************
 *  LifecycleAwareScheduler.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
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

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.vlc.BuildConfig
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timerTask

/**
 * Callback for [LifecycleAwareScheduler].
 * It will be used to listen to action trigger and cancellation
 */
interface SchedulerCallback : LifecycleOwner {
    /**
     * Called when the action should be performed.
     * It's guaranteed to be ran on the main thread
     *
     * @param id the action id
     */
    fun onTaskTriggered(id: String)

    /**
     * (Optional) Called when the action is cancelled
     *
     * @param id the action id
     */
    fun onTaskCanceled(id: String) {}
}

/**
 * Thread safe and lifecycle aware scheduler class.
 * Its goal is to schedule tasks no matter what the thread it's launched in,
 * cancel these tasks when the calling component is paused / destroyed
 * and manage a task list
 * This is a safer and easier to use replacement for Handler
 *
 * @property callback the listening [LifecycleOwner] to launch it
 */
class LifecycleAwareScheduler(private val callback: SchedulerCallback) : DefaultLifecycleObserver {

    private val timer = Timer()
    private val timeTasks = HashMap<String, TimerTask>()

    init {
        if (BuildConfig.DEBUG) Log.d("LifecycleAwareScheduler", "Creating LifecycleAwareScheduler for $callback")
    }

    /**
     * Start an immediate action.
     * Can be called in any thread
     *
     * @param id the action id
     */
    fun startAction(id: String) {
        scheduleAction(id, 0L)
    }

    /**
     * Schedule an action to be called in [delay]ms.
     * Can be called in any thread
     *
     * @param id the action id
     * @param delay the delay before the action is ran
     */
    fun scheduleAction(id: String, delay: Long) {
        if (BuildConfig.DEBUG) Log.d("LifecycleAwareScheduler", "Scheduling action for $callback on thread ${Thread.currentThread()} with id $id")
        callback.lifecycle.addObserver(this@LifecycleAwareScheduler)
        if (timeTasks.keys.contains(id)) cancelAction(id)
        timeTasks[id] = timerTask {
            callback.lifecycleScope.launch(Dispatchers.Main) { callback.onTaskTriggered(id) }
        }
        timer.schedule(timeTasks[id], delay)
    }

    /**
     * Cancel an existing action
     *
     * @param id the action id
     * @return true if an action has been canceled, false otherwise
     */
    fun cancelAction(id: String): Boolean {
        if (BuildConfig.DEBUG) Log.d("LifecycleAwareScheduler", "Canceling action for $callback on thread ${Thread.currentThread()} with id $id")
        if (timeTasks.keys.contains(id)) {
            timeTasks[id]?.cancel()
            callback.onTaskCanceled(id)
            timeTasks.remove(id)
            return true
        }
        return false
    }

    /**
     * Cancel the timer and off-hook from lifecycle callbacks
     */
    private fun discardTimer() {
        timeTasks.forEach { callback.onTaskCanceled(it.key) }
        timer.cancel()
        callback.lifecycle.removeObserver(this)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        timeTasks.forEach {
            it.value.cancel()
            callback.onTaskCanceled(it.key)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        discardTimer()
    }
}