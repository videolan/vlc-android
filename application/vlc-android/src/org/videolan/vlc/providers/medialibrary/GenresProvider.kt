/*****************************************************************************
 * GenresProvider.kt
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.tools.Settings
import org.videolan.vlc.viewmodels.SortableModel

class GenresProvider(context: Context, model: SortableModel) : MedialibraryProvider<Genre>(context, model)  {

    override fun getAll() : Array<Genre> = medialibrary.getGenres(sort, desc, Settings.includeMissing, onlyFavorites)

    override fun getPage(loadSize: Int, startposition: Int) : Array<Genre> {
        val list = if (model.filterQuery == null) medialibrary.getPagedGenres(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        else medialibrary.searchGenre(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    override fun getTotalCount() = if (model.filterQuery == null) medialibrary.genresCount else medialibrary.getGenresCount(model.filterQuery)
}