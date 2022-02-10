/*****************************************************************************
 * RenderersDialog.java
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
package org.videolan.vlc.gui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.RendererItem
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.databinding.DialogRenderersBinding
import org.videolan.vlc.databinding.ItemRendererBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class RenderersDialog : DialogFragment() {
    private var renderers = RendererDelegate.renderers.value
    private lateinit var dialogRenderersBinding: DialogRenderersBinding
    private val adapter = RendererAdapter()
    private val clickHandler = RendererClickhandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RendererDelegate.renderers.observe(this) {
            if (it !== null) {
                renderers = it
                adapter.update(it)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        dialogRenderersBinding = DialogRenderersBinding.inflate(inflater, null, false)
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogRenderersBinding.root)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialogRenderersBinding = DialogRenderersBinding.inflate(inflater, container, false)
        return dialogRenderersBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialogRenderersBinding.holder = clickHandler
        dialogRenderersBinding.renderersList.layoutManager = LinearLayoutManager(view.context)
        dialogRenderersBinding.renderersList.adapter = adapter
        dialogRenderersBinding.renderersDisconnect.isEnabled = PlaybackService.hasRenderer()
        dialogRenderersBinding.renderersDisconnect.setTextColor(ContextCompat.getColor(view.context, if (PlaybackService.hasRenderer()) R.color.orange800 else R.color.grey400))
        adapter.update(renderers)
    }

    private inner class RendererAdapter : DiffUtilAdapter<RendererItem, SelectorViewHolder<ItemRendererBinding>>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectorViewHolder<ItemRendererBinding> {
            val binding = ItemRendererBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            binding.clicHandler = clickHandler
            return SelectorViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SelectorViewHolder<ItemRendererBinding>, position: Int) {
            holder.binding.renderer = renderers[position]
            if (renderers[position] == PlaybackService.renderer.value)
            holder.binding.rendererName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.orange800))
        }

        override fun getItemCount() = dataset.size

        override fun onUpdateFinished() {}
    }

    inner class RendererClickhandler {
        fun connect(item: RendererItem?) {
            PlaybackService.renderer.value = item
            dismissAllowingStateLoss()
            item?.run {
                activity?.window?.findViewById<View>(R.id.audio_player_container)?.let {
                    UiTools.snacker(requireActivity(), getString(R.string.casting_connected_renderer, displayName))
                }
            }
        }
    }
}
