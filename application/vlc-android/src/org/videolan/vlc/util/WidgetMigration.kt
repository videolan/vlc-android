/*
 * ************************************************************************
 *  WidgetMigration.kt
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

package org.videolan.vlc.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.dialogs.WidgetMigrationDialog
import org.videolan.vlc.widget.VLCAppWidgetProviderBlack
import org.videolan.vlc.widget.VLCAppWidgetProviderWhite


private const val WIDGET_MIGRATION_KEY = "widget_migration_key"
object WidgetMigration {
    fun launchIfNeeded(context: AppCompatActivity):Boolean {
        val settings = Settings.getInstance(context)
        if (!settings.getBoolean(WIDGET_MIGRATION_KEY, false)) {
            AppWidgetManager.getInstance(context)?.let {manager ->
                if (manager.getAppWidgetIds(ComponentName(context, VLCAppWidgetProviderWhite::class.java)).isNotEmpty() || manager.getAppWidgetIds(ComponentName(context, VLCAppWidgetProviderBlack::class.java)).isNotEmpty()) {
                    val widgetMigrationDialog = WidgetMigrationDialog()
                    widgetMigrationDialog.show(context.supportFragmentManager, "fragment_widget_migration")
                    return true
                }
                val pm: PackageManager = context.application.packageManager
                pm.setComponentEnabledSetting(ComponentName(context.application, VLCAppWidgetProviderBlack::class.java), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(ComponentName(context.application, VLCAppWidgetProviderWhite::class.java), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            settings.putSingle(WIDGET_MIGRATION_KEY, true)
        }
        return false
    }
}