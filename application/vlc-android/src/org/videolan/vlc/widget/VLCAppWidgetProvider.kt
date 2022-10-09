/*****************************************************************************
 * VLCAppWidgetProvider.java
 *
 * Copyright © 2012 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.widget

import android.annotation.TargetApi
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import org.videolan.resources.buildPkgString
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity

abstract class VLCAppWidgetProvider : AppWidgetProvider() {

    protected abstract fun getlayout(): Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        /* init widget */
        onReceive(context, Intent(ACTION_WIDGET_INIT))

        /* ask a refresh from the service if there is one */
        context.sendBroadcast(Intent(ACTION_WIDGET_INIT).setPackage(BuildConfig.APP_ID))
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null || !action.startsWith(ACTION_WIDGET_PREFIX)) {
            super.onReceive(context, intent)
            return
        }

        val views = RemoteViews(BuildConfig.APP_ID, getlayout())
        val partial = ACTION_WIDGET_INIT != action
        val appCtx = context.applicationContext
        val iVlc = Intent(appCtx, StartActivity::class.java)
        val piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, piVlc)

        applyUpdate(context, views, partial)
    }

    private fun applyUpdate(context: Context, views: RemoteViews, partial: Boolean) {
        val widget = ComponentName(context, this.javaClass)
        val manager = AppWidgetManager.getInstance(context)
        if (partial)
            manager.partiallyUpdateAppWidget(manager.getAppWidgetIds(widget), views)
        else
            manager.updateAppWidget(widget, views)
    }

    protected abstract fun getPlayPauseImage(isPlaying: Boolean): Int

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.sendBroadcast(Intent(ACTION_WIDGET_ENABLED, null, context.applicationContext, PlaybackService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.sendBroadcast(Intent(ACTION_WIDGET_DISABLED, null, context.applicationContext, PlaybackService::class.java))
    }

    companion object {
        const val TAG = "VLC/VLCAppWidgetProvider"
        val ACTION_WIDGET_PREFIX = "widget.".buildPkgString()
        val ACTION_WIDGET_INIT = ACTION_WIDGET_PREFIX + "INIT"
        val ACTION_WIDGET_UPDATE = ACTION_WIDGET_PREFIX + "UPDATE"
        val ACTION_WIDGET_UPDATE_COVER = ACTION_WIDGET_PREFIX + "UPDATE_COVER"
        val ACTION_WIDGET_UPDATE_POSITION = ACTION_WIDGET_PREFIX + "UPDATE_POSITION"
        val ACTION_WIDGET_ENABLED = ACTION_WIDGET_PREFIX + "ENABLED"
        val ACTION_WIDGET_DISABLED = ACTION_WIDGET_PREFIX + "DISABLED"
    }
}