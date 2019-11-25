/*****************************************************************************
 * RendererDelegate.java
 *
 * Copyright © 2017 VLC authors and VideoLAN
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
 */
package org.videolan.vlc

import kotlinx.coroutines.*
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.tools.retry
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.isAppStarted
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object RendererDelegate : RendererDiscoverer.EventListener {

    private val TAG = "VLC/RendererDelegate"
    private val discoverers = ArrayList<RendererDiscoverer>()
    val renderers : LiveDataset<RendererItem> = LiveDataset()

    @Volatile private var started = false

    init {
        ExternalMonitor.connected.observeForever { AppScope.launch { if (it == true) start() else stop() } }
    }

    suspend fun start() {
        if (started) return
        val libVlc = VLCApplication.appContext.let {
            withContext(Dispatchers.IO) { VLCInstance.get(it) }
        }
        started = true
        for (discoverer in RendererDiscoverer.list(libVlc)) {
            val rd = RendererDiscoverer(libVlc, discoverer.name)
            discoverers.add(rd)
            rd.setEventListener(this@RendererDelegate)
            retry(5, 1000L) { if (!rd.isReleased) rd.start() else false }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        for (discoverer in discoverers) discoverer.stop()
        if (isAppStarted() || PlaybackService.service.value?.run { !isPlaying } != false) {
            PlaybackService.renderer.value = null
        }
        clear()
    }

    private fun clear() {
        discoverers.clear()
        renderers.clear()
    }

    override fun onEvent(event: RendererDiscoverer.Event?) {
        when (event?.type) {
            RendererDiscoverer.Event.ItemAdded -> renderers.add(event.item)
            RendererDiscoverer.Event.ItemDeleted -> renderers.remove(event.item)
        }
    }
}
