/*
 * ************************************************************************
 *  TvMediaViewModel.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.HEADER_PERMISSION
import org.videolan.tools.getContextWithLocale

open class TvMediaViewModel(app: Application) : AndroidViewModel(app) {

    fun getContext() = getApplication<Application>().applicationContext.getContextWithLocale(AppContextProvider.locale)

    suspend fun setLoading(loadingField: MutableLiveData<Boolean>, loadingValue: Boolean) = withContext(Dispatchers.Main) {
        loadingField.value = loadingValue
    }

    suspend fun showPermissionItem(list: MutableLiveData<List<MediaLibraryItem>>) = withContext(Dispatchers.Main) {
        list.value = listOf(DummyItem(HEADER_PERMISSION, getContext().getString(org.videolan.vlc.R.string.permission_media), getContext().getString(org.videolan.vlc.R.string.permission_ask_again)))
    }
}
