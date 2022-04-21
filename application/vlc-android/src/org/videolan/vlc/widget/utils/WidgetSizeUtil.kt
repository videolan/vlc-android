/*
 * ************************************************************************
 *  WidgetSizeUtil.kt
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

package org.videolan.vlc.widget.utils

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT

object WidgetSizeUtil {


    fun getWidgetsSize(context: Context, widgetId: Int): Pair<Int, Int> {
        val isPortrait = context.resources.configuration.orientation == ORIENTATION_PORTRAIT
        return getWidgetWidth(context, isPortrait, widgetId) to getWidgetHeight(context, isPortrait, widgetId)
    }

    fun getAppWidgetManager(context: Context) = AppWidgetManager.getInstance(context)

    private fun getWidgetWidth(context: Context, isPortrait: Boolean, widgetId: Int): Int =
            if (isPortrait) {
                getWidgetSizeInDp(context, widgetId, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            } else {
                getWidgetSizeInDp(context, widgetId, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            }

    private fun getWidgetHeight(context: Context, isPortrait: Boolean, widgetId: Int): Int =
            if (isPortrait) {
                getWidgetSizeInDp(context, widgetId, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            } else {
                getWidgetSizeInDp(context, widgetId, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            }

    private fun getWidgetSizeInDp(context: Context, widgetId: Int, key: String): Int =
            getAppWidgetManager(context).getAppWidgetOptions(widgetId).getInt(key, 0)

    private fun Context.dip(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}