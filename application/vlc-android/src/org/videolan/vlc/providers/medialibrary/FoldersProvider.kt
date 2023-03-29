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
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.tools.Settings
import org.videolan.vlc.viewmodels.SortableModel

class FoldersProvider(context: Context, model: SortableModel, val type: Int) : MedialibraryProvider<Folder>(context, model) {
    override fun getAll() : Array<Folder> = medialibrary.getFolders(type, sort, desc, Settings.includeMissing, onlyFavorites, getTotalCount(), 0)

    override fun getTotalCount() = if (model.filterQuery.isNullOrEmpty()) medialibrary.getFoldersCount(type) else medialibrary.getFoldersCount(model.filterQuery)

    override fun getPage(loadSize: Int, startposition: Int) : Array<Folder> = if (model.filterQuery.isNullOrEmpty()) {
        medialibrary.getFolders(type, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition).also { completeHeaders(it, startposition) }
    } else {
        medialibrary.searchFolders(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
    }.also { if (Settings.showTvUi) completeHeaders(it, startposition) }
}