/*****************************************************************************
 * PBSMedialibraryReceiver.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.startMedialibrary
import java.util.*

internal class PBSMedialibraryReceiver(private val service: PlaybackService) : BroadcastReceiver() {
    private val pendingActions by lazy(LazyThreadSafetyMode.NONE) { LinkedList<Runnable>() }

    init {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(service).registerReceiver(this, IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY))
    }

    override fun onReceive(context: Context, intent: Intent) {
        service.libraryReceiver = null
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(service).unregisterReceiver(this)
        for (r in pendingActions) r.run()
    }

    fun addAction(r: Runnable) = pendingActions.add(r)
}

internal fun PlaybackService.registerMedialibrary(action: Runnable?) {
    if (!Permissions.canReadStorage(this)) return
    if (libraryReceiver == null) {
        libraryReceiver = PBSMedialibraryReceiver(this)
        startMedialibrary(parse = false)
    }
    if (action != null) libraryReceiver?.addAction(action)
}

internal fun PlaybackService.runOnceReady(action: Runnable) {
    when {
        medialibrary.isInitiated -> action.run()
        libraryReceiver == null -> registerMedialibrary(action)
        else -> libraryReceiver?.addAction(action)
    }
}