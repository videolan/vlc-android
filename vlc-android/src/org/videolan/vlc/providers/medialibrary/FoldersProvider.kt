/*****************************************************************************
 * FoldersProvider.kt
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.providers.medialibrary

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class FoldersProvider(context: Context, scope: SortableModel, val type: Int) : MedialibraryProvider<AbstractFolder>(context, scope) {
    override fun getAll() : Array<AbstractFolder> = medialibrary.getFolders(type, sort, desc, getTotalCount(), 0)

    override fun getTotalCount() = medialibrary.getFoldersCount(type)

    override fun getPage(loadSize: Int, startposition: Int) : Array<AbstractFolder> = medialibrary.getFolders(type, sort, desc, loadSize, startposition).also { completeHeaders(it, startposition) }

}