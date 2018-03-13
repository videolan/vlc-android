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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.retry
import java.util.*

object RendererDelegate : RendererDiscoverer.EventListener {

    private val TAG = "VLC/RendererDelegate"
    private val mDiscoverers = ArrayList<RendererDiscoverer>()
    val renderers : LiveData<MutableList<RendererItem>> = MutableLiveData()

    @Volatile private var started = false
    val selectedRenderer: LiveData<RendererItem> = MutableLiveData()

    init {
        ExternalMonitor.connected.observeForever { launch(UI, CoroutineStart.UNDISPATCHED) { if (it == true) start() else stop() } }
    }

    suspend fun start() {
        if (started) return
        started = true
        val libVlc = withContext(CommonPool) { VLCInstance.get() }
        for (discoverer in RendererDiscoverer.list(libVlc)) {
            val rd = RendererDiscoverer(libVlc, discoverer.name)
            mDiscoverers.add(rd)
            rd.setEventListener(this@RendererDelegate)
            retry(5, 1000L) { rd.start() }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        for (discoverer in mDiscoverers) discoverer.stop()
        clear()
        (renderers as MutableLiveData).value = mutableListOf()
        (selectedRenderer as MutableLiveData).value = null
    }

    private fun clear() {
        mDiscoverers.clear()
        renderers.value?.apply {
            for (renderer in this) renderer.release()
            this.clear()
        }
    }

    override fun onEvent(event: RendererDiscoverer.Event?) {
        (renderers as MutableLiveData).value = when (event?.type) {
            RendererDiscoverer.Event.ItemAdded -> { (renderers.value ?: mutableListOf()).apply { add(event.item) } }
            RendererDiscoverer.Event.ItemDeleted -> { renderers.value?.apply { remove(event.item); event.item.release() } }
            else -> null
        }
    }

    fun selectRenderer(item: RendererItem?) {
        (selectedRenderer as MutableLiveData).value = item
    }

    fun hasRenderer() = selectedRenderer.value !== null
}
