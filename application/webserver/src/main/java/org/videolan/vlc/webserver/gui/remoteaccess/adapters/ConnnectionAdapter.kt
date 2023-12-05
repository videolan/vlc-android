/*
 * ************************************************************************
 *  ConnectionAdapter.kt
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

package org.videolan.vlc.webserver.gui.remoteaccess.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.webserver.RemoteAccessServer
import org.videolan.vlc.webserver.databinding.RemoteAccessConnectionItemBinding

class ConnnectionAdapter(private val layoutInflater: LayoutInflater, var connections:List<RemoteAccessServer.RemoteAccessConnection>) : RecyclerView.Adapter<ConnnectionAdapter.ViewHolder>() {



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(RemoteAccessConnectionItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemCount() = connections.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val serverConnection = connections[position]
        holder.binding.connectionTitle.text = serverConnection.ip
    }

    inner class ViewHolder(binding: RemoteAccessConnectionItemBinding) : SelectorViewHolder<RemoteAccessConnectionItemBinding>(binding)


}
