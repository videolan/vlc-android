/*****************************************************************************
 * PagedArtistsModel.kt
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

package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Artist
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.util.EmptyMLCallbacks


@ExperimentalCoroutinesApi
class PagedArtistsModel(context: Context, showAll: Boolean = false): MLPagedModel<Artist>(context), Medialibrary.ArtistsCb by EmptyMLCallbacks {

    override val provider = ArtistsProvider(context, this, showAll)

    var showAll : Boolean
        get() = provider.showAll
        set(value) {
            provider.showAll = value
        }

    init {
        medialibrary.addArtistsCb(this)
        if (medialibrary.isStarted) refresh()
    }
    override fun onArtistsAdded() {
        refresh()
    }

    override fun onCleared() {
        medialibrary.removeArtistsCb(this)
        super.onCleared()
    }

    class Factory(private val context: Context, private val showAll: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedArtistsModel(context, showAll) as T
        }
    }
}