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
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.resources.*
import org.videolan.tools.runIO
import org.videolan.tools.runOnMainThread
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.util.getPendingIntent
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
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

        if (!partial) {
            /* commands */
            val appCtx = context.applicationContext
            val iBackward = Intent(ACTION_REMOTE_BACKWARD, null, appCtx, PlaybackService::class.java)
            val iPlay = Intent(ACTION_REMOTE_PLAYPAUSE, null, appCtx, PlaybackService::class.java)
            val iStop = Intent(ACTION_REMOTE_STOP, null, appCtx, PlaybackService::class.java)
            val iForward = Intent(ACTION_REMOTE_FORWARD, null, appCtx, PlaybackService::class.java)
            val iVlc = Intent(appCtx, StartActivity::class.java)

            val piBackward = context.getPendingIntent(iBackward)
            val piPlay = context.getPendingIntent(iPlay)
            val piStop = context.getPendingIntent(iStop)
            val piForward = context.getPendingIntent(iForward)
            val piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT)

            views.setOnClickPendingIntent(R.id.backward, piBackward)
            views.setOnClickPendingIntent(R.id.play_pause, piPlay)
            views.setOnClickPendingIntent(R.id.stop, piStop)
            views.setOnClickPendingIntent(R.id.forward, piForward)
            views.setOnClickPendingIntent(R.id.cover, piVlc)
            views.setOnClickPendingIntent(R.id.widget_container, piVlc)
            if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
                val black = this is VLCAppWidgetProviderBlack
                views.setImageViewResource(R.id.forward, if (black) R.drawable.ic_widget_previous_w else R.drawable.ic_widget_previous)
                views.setImageViewResource(R.id.backward, if (black) R.drawable.ic_widget_next_w else R.drawable.ic_widget_next)
            }
        }

        when {
            ACTION_WIDGET_UPDATE == action -> {
                val title = intent.getStringExtra("title")
                val artist = intent.getStringExtra("artist")
                val isplaying = intent.getBooleanExtra("isplaying", false)

                views.setTextViewText(R.id.songName, title)
                views.setTextViewText(R.id.artist, artist)
                views.setImageViewResource(R.id.play_pause, getPlayPauseImage(isplaying))
            }
            ACTION_WIDGET_UPDATE_COVER == action -> {
                val artworkMrl = intent.getStringExtra("artworkMrl")
                if (!artworkMrl.isNullOrEmpty()) {
                    runIO(Runnable {
                        val cover = AudioUtil.readCoverBitmap(Uri.decode(artworkMrl), 320)
                        val wm = context.getSystemService<WindowManager>()!!
                        val dm = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
                        runOnMainThread(Runnable {
                            if (cover != null) {
                                if (cover.byteSize() < dm.widthPixels * dm.heightPixels * 6) views.setImageViewBitmap(R.id.cover, cover)
                            } else
                                views.setImageViewResource(R.id.cover, R.drawable.icon)
                            views.setProgressBar(R.id.timeline, 100, 0, false)
                            applyUpdate(context, views, partial)
                        })
                    })
                } else
                    views.setImageViewResource(R.id.cover, R.drawable.icon)
                views.setProgressBar(R.id.timeline, 100, 0, false)
            }
            ACTION_WIDGET_UPDATE_POSITION == action -> {
                val pos = intent.getFloatExtra("position", 0f)
                views.setProgressBar(R.id.timeline, 100, (100 * pos).toInt(), false)
            }
        }

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

fun Bitmap.byteSize(): Int {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
        return allocationByteCount
    }
    return rowBytes * height
}