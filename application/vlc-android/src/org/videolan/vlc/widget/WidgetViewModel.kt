/*
 * ************************************************************************
 *  WidgetViewModel.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.widget

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import org.videolan.vlc.mediadb.models.Widget
import org.videolan.vlc.repository.WidgetRepository

class WidgetViewModel(context: Context, id:Int) : AndroidViewModel(context.applicationContext as Application) {
    private val widgetRepository = WidgetRepository.getInstance(context)

    val widget: LiveData<Widget> = widgetRepository.getWidgetFlow(id).asLiveData(viewModelScope.coroutineContext)

    suspend fun create(context:Context, appWidgetId:Int) {
        widgetRepository.createNew(context, appWidgetId)
    }

    class Factory(val context: Context, private val id:Int) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WidgetViewModel(context.applicationContext ,id) as T
        }
    }
}