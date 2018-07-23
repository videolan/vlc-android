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
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
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
    private val listeners = LinkedList<RendererListener>()
    private val players = LinkedList<RendererPlayer>()

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
            retry(5, 1000L) { if (!rd.isReleased) rd.start() else false }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        for (discoverer in mDiscoverers) discoverer.stop()
        clear()
        onRenderersChanged()
        cbActor.offer(RendererChanged(null))
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

    fun addListener(listener: RendererListener) = cbActor.offer(AddListener(listener))

    fun removeListener(listener: RendererListener) = cbActor.offer(RemoveListener(listener))

    private fun onRenderersChanged() = cbActor.offer(RenderersChanged())

    fun selectRenderer(item: RendererItem?) {
        selectedRenderer = item
        cbActor.offer(RendererChanged(item))
    }

    fun addPlayerListener(player: RendererPlayer) = cbActor.offer(AddPlayer(player))

    fun removePlayerListener(player: RendererPlayer) = cbActor.offer(RemovePlayer(player))

    private val cbActor = actor<CbAction>(UI, Channel.UNLIMITED) {
        for (action in channel) when (action) {
            is AddPlayer -> players.add(action.player)
            is RemovePlayer -> players.remove(action.player)
            is RendererChanged -> for (player in players) player.onRendererChanged(action.renderer)
            is AddListener -> listeners.add(action.listener)
            is RemoveListener -> listeners.remove(action.listener)
            is RenderersChanged -> for (listener in listeners) listener.onRenderersChanged(renderers.isEmpty())
        }
    }
}

sealed class CbAction
private class AddPlayer(val player: RendererDelegate.RendererPlayer) : CbAction()
private class RemovePlayer(val player: RendererDelegate.RendererPlayer) : CbAction()
private class RendererChanged(val renderer: RendererItem?) : CbAction()
private class AddListener(val listener: RendererDelegate.RendererListener) : CbAction()
private class RemoveListener(val listener: RendererDelegate.RendererListener) : CbAction()
private class RenderersChanged : CbAction()
