/*
 * ************************************************************************
 *  WidgetRepository.kt
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

package org.videolan.vlc.repository

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.R
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.WidgetDao
import org.videolan.vlc.mediadb.models.Widget
import org.videolan.vlc.widget.utils.WidgetCache


class WidgetRepository(private val widgetDao: WidgetDao) {

    suspend fun getAllWidgets() = withContext(Dispatchers.IO) {
        widgetDao.getAll()
    }

    suspend fun getWidget(id: Int) = withContext(Dispatchers.IO) {
        widgetDao.get(id)
    }

    fun getWidgetFlow(id: Int): Flow<Widget> {
        return widgetDao.getFlow(id)
    }

    suspend fun addWidget(widget:Widget) = withContext(Dispatchers.IO) {
        widgetDao.insert(widget)
    }

    suspend fun updateWidget(widget:Widget, preventCacheClear:Boolean = false) {
        if (!preventCacheClear) WidgetCache.clear(widget)
        withContext(Dispatchers.IO) {
            widgetDao.update(widget)
        }
    }

    suspend fun deleteWidget(id: Int) = withContext(Dispatchers.IO) {
        widgetDao.delete(id)
    }

    suspend fun createNew(context: Context, appWidgetId: Int): Widget {
        val widget = Widget(appWidgetId, 0, 0, 0, 0, true, ContextCompat.getColor(context, R.color.black), ContextCompat.getColor(context, R.color.white), 10, 10, 100, showConfigure = true, showSeek = true, showCover = true)
        addWidget(widget)
        return widget
    }


    companion object : SingletonHolder<WidgetRepository, Context>({ WidgetRepository(MediaDatabase.getInstance(it).widgetDao()) })
}
