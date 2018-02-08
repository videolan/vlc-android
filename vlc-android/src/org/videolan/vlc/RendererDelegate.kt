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

import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.retry
import java.util.*

object RendererDelegate : RendererDiscoverer.EventListener, ExternalMonitor.NetworkObserver {

    private val TAG = "VLC/RendererDelegate"
    private val mDiscoverers = ArrayList<RendererDiscoverer>()
    val renderers = ArrayList<RendererItem>()
    private val mListeners = LinkedList<RendererListener>()
    private val mPlayers = LinkedList<RendererPlayer>()

    @Volatile private var started = false
    var selectedRenderer: RendererItem? = null
        private set

    init {
        ExternalMonitor.subscribeNetworkCb(this)
    }

    interface RendererListener {
        fun onRenderersChanged(empty: Boolean)
    }

    interface RendererPlayer {
        fun onRendererChanged(renderer: RendererItem?)
    }

    suspend fun start() {
        if (started) return
        started = true
        val libVlc = async { VLCInstance.get() }.await()
        for (discoverer in RendererDiscoverer.list(libVlc)) {
            val rd = RendererDiscoverer(libVlc, discoverer.name)
            mDiscoverers.add(rd)
            rd.setEventListener(this@RendererDelegate)
            retry(5, 1000L) { rd.start() }
        }
    }

    suspend fun stop() {
        if (!started) return
        started = false
        for (discoverer in mDiscoverers) discoverer.stop()
        clear()
        onRenderersChanged()
        for (player in mPlayers) player.onRendererChanged(null)
    }

    private fun clear() {
        mDiscoverers.clear()
        for (renderer in renderers) renderer.release()
        renderers.clear()
    }

    override fun onNetworkConnectionChanged(connected: Boolean) {
        launch(UI, CoroutineStart.UNDISPATCHED) { if (connected) start() else stop() }
    }

    override fun onEvent(event: RendererDiscoverer.Event?) {
        when (event?.type) {
            RendererDiscoverer.Event.ItemAdded -> { renderers.add(event.item) }
            RendererDiscoverer.Event.ItemDeleted -> { renderers.remove(event.item); event.item.release() }
            else -> return
        }
        onRenderersChanged()
    }

    fun addListener(listener: RendererListener) = mListeners.add(listener)

    fun removeListener(listener: RendererListener) = mListeners.remove(listener)

    private fun onRenderersChanged() {
        for (listener in mListeners) listener.onRenderersChanged(renderers.isEmpty())
    }

    fun selectRenderer(item: RendererItem?) {
        selectedRenderer = item
        for (player in mPlayers) player.onRendererChanged(item)
    }

    fun addPlayerListener(listener: RendererPlayer) = mPlayers.add(listener)

    fun removePlayerListener(listener: RendererPlayer) = mPlayers.remove(listener)
}
