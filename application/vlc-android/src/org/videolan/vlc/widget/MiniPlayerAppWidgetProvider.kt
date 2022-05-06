/*
 * ************************************************************************
 *  MiniPlayerAppWidgetProvider.kt
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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import androidx.palette.graphics.Palette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.AppScope
import org.videolan.tools.dp
import org.videolan.tools.runIO
import org.videolan.tools.runOnMainThread
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.getColoredBitmapFromColor
import org.videolan.vlc.repository.WidgetRepository
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.getPendingIntent
import org.videolan.vlc.widget.utils.*
import java.util.*


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MiniPlayerAppWidgetProvider : AppWidgetProvider() {
    private lateinit var _widgetRepository: WidgetRepository


    private fun getWidgetRepository(context: Context) = if (::_widgetRepository.isInitialized) _widgetRepository else {
        _widgetRepository = WidgetRepository.getInstance(context)
        _widgetRepository
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)


        /* init widget */
        onReceive(context, Intent(ACTION_WIDGET_INIT))

        /* ask a refresh from the service if there is one */
        context.sendBroadcast(Intent(ACTION_WIDGET_INIT).setPackage(BuildConfig.APP_ID))
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        AppScope.launch {
            appWidgetIds?.forEach {
                getWidgetRepository(context!!).deleteWidget(it)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (BuildConfig.DEBUG) Log.d("AppWidget", "onReceive: ${intent.action}")
        val action = intent.action
        val partial = ACTION_WIDGET_INIT != action

        val widgetRepository = getWidgetRepository(context)

        AppScope.launch {
            val extraId = intent.getIntExtra("ID", -1)
            if (extraId == -1)
                widgetRepository.getAllWidgets().forEach {
                    if (it.widgetId != 0) applyUpdate(context, layoutWidget(context, it.widgetId, intent), partial, it.widgetId)
                }
            else widgetRepository.getWidget(extraId)?.let {
                if (it.widgetId != 0) applyUpdate(context, layoutWidget(context, it.widgetId, intent), partial, it.widgetId)
            }
        }


        if (action == null || !action.startsWith(VLCAppWidgetProvider.ACTION_WIDGET_PREFIX)) {
            super.onReceive(context, intent)
            return
        }


    }

    suspend fun layoutWidget(context: Context, appWidgetId: Int, intent: Intent, forPreview: Boolean = false, previewBitmap: Bitmap? = null, previewPalette: Palette? = null): RemoteViews {

        val partial = ACTION_WIDGET_INIT != intent.action
        if (BuildConfig.DEBUG) Log.d("AppWidget", "layoutWidget widget id $appWidgetId / partial: $partial / action = ${intent.action}")

        val widgetRepository = getWidgetRepository(context)
        val persitedWidget = widgetRepository.getWidget(appWidgetId)
                ?: widgetRepository.createNew(context, appWidgetId)

        val widgetCacheEntry = if (forPreview) WidgetCacheEntry(persitedWidget, getFakeMedia()) else WidgetCache.getEntry(persitedWidget)
                ?: WidgetCache.addEntry(persitedWidget)
        if (!partial && !forPreview) widgetCacheEntry.reset()


        val palette: Palette? = if (forPreview) previewPalette else widgetCacheEntry.palette
        val foregroundColorSecondary = widgetCacheEntry.widget.getForegroundColor(context, true, palette = palette)
        val foregroundColor = widgetCacheEntry.widget.getForegroundColor(context, palette = palette)
        val backgroundColor = widgetCacheEntry.widget.getBackgroundColor(context, palette = palette)
        val secondaryBackgroundColor = widgetCacheEntry.widget.getBackgroundColor(context, palette = palette, secondary = true)

        val colorChanged = widgetCacheEntry.foregroundColor != foregroundColor
        widgetCacheEntry.foregroundColor = foregroundColor


        //determine layout

        val size = WidgetSizeUtil.getWidgetsSize(context, appWidgetId)
        if (size.first != 0 && size.second != 0 && (widgetCacheEntry.widget.width != size.first || widgetCacheEntry.widget.height != size.second)) {
            widgetCacheEntry.widget.width = size.first
            widgetCacheEntry.widget.height = size.second
            if (BuildConfig.DEBUG) Log.d("AppWidget", "Updating widget entry to: $widgetCacheEntry.widget")

            widgetRepository.updateWidget(widgetCacheEntry.widget)
        }
        val correctedSize = if (size.first == 0 && size.second == 0) {
            if (BuildConfig.DEBUG) Log.d("AppWidget", "Size is 0. Getting size from db: $widgetCacheEntry.widget")
            Pair(widgetCacheEntry.widget.width, widgetCacheEntry.widget.height)
        } else size
        if (BuildConfig.DEBUG) Log.d("AppWidget", "New widget size by provider: ${correctedSize.first} / ${correctedSize.second}")
        val widgetType = when {
            correctedSize.first > 220 && correctedSize.second > 220 -> WidgetType.MACRO
            correctedSize.first > 128 && correctedSize.second > 128 -> WidgetType.MICRO
            correctedSize.first > 220 && correctedSize.second > 0 -> WidgetType.MINI
            else -> WidgetType.PILL
        }

        val views = RemoteViews(context.packageName, widgetType.layout)

        if (!partial) {
            /* commands */
            val appCtx = context.applicationContext
            val iBackward = Intent(ACTION_REMOTE_BACKWARD, null, appCtx, PlaybackService::class.java)
            val iPlay = Intent(ACTION_REMOTE_PLAYPAUSE, null, appCtx, PlaybackService::class.java)
            val iStop = Intent(ACTION_REMOTE_STOP, null, appCtx, PlaybackService::class.java)
            val iForward = Intent(ACTION_REMOTE_FORWARD, null, appCtx, PlaybackService::class.java)
            val iSeekForward = Intent(ACTION_REMOTE_SEEK_FORWARD, null, appCtx, PlaybackService::class.java).apply { putExtra(EXTRA_SEEK_DELAY, widgetCacheEntry.widget.forwardDelay.toLong()) }
            val iSeekBackward = Intent(ACTION_REMOTE_SEEK_BACKWARD, null, appCtx, PlaybackService::class.java).apply { putExtra(EXTRA_SEEK_DELAY, widgetCacheEntry.widget.rewindDelay.toLong()) }
            val iVlc = Intent(appCtx, StartActivity::class.java)
            val iConfigure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = ComponentName(context, MiniPlayerConfigureActivity::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                //we have to add a data to this intent to differentiate intents from different widget instances
                data = Uri.parse("vlc://mini_widget/$appWidgetId")
            }

            val piBackward = context.getPendingIntent(iBackward)
            val piPlay = context.getPendingIntent(iPlay)
            val piStop = context.getPendingIntent(iStop)
            val piForward = context.getPendingIntent(iForward)
            val piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT)
            val piConfigure = PendingIntent.getActivity(context, 0, iConfigure, PendingIntent.FLAG_UPDATE_CURRENT)
            val piSeekForward = context.getPendingIntent(iSeekForward)
            val piSeekBackward = context.getPendingIntent(iSeekBackward)


            views.setOnClickPendingIntent(R.id.backward, piBackward)
            views.setOnClickPendingIntent(R.id.play_pause, piPlay)
            views.setOnClickPendingIntent(R.id.stop, piStop)
            views.setOnClickPendingIntent(R.id.forward, piForward)
            views.setOnClickPendingIntent(R.id.cover, piVlc)
            views.setOnClickPendingIntent(R.id.app_icon, piVlc)
            views.setOnClickPendingIntent(R.id.widget_container, piVlc)
            views.setOnClickPendingIntent(R.id.widget_configure, piConfigure)
            views.setOnClickPendingIntent(R.id.seek_rewind, piSeekBackward)
            views.setOnClickPendingIntent(R.id.seek_forward, piSeekForward)
        }

        val service = PlaybackService.serviceFlow.value
        val playing = service?.isPlaying == true || forPreview
        if (colorChanged) {
            if (BuildConfig.DEBUG) Log.d("AppWidget", "Bugfix Color changed!!! for widget $appWidgetId // forPreview $forPreview")
            if (android.text.TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
                views.setImageViewBitmap(R.id.forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_previous_normal, foregroundColor))
                views.setImageViewBitmap(R.id.backward, context.getColoredBitmapFromColor(R.drawable.ic_widget_next_normal, foregroundColor))
                views.setImageViewBitmap(R.id.seek_rewind, context.getColoredBitmapFromColor(R.drawable.ic_widget_forward_10, foregroundColor))
                views.setImageViewBitmap(R.id.seek_forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_rewind_10, foregroundColor))
                views.setImageViewBitmap(R.id.backward, context.getColoredBitmapFromColor(R.drawable.ic_widget_next_normal, foregroundColor))
            } else {
                views.setImageViewBitmap(R.id.forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_next_normal, foregroundColor))
                views.setImageViewBitmap(R.id.backward, context.getColoredBitmapFromColor(R.drawable.ic_widget_previous_normal, foregroundColor))
                views.setImageViewBitmap(R.id.seek_rewind, context.getColoredBitmapFromColor(R.drawable.ic_widget_rewind_10, foregroundColor))
                views.setImageViewBitmap(R.id.seek_forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_forward_10, foregroundColor))
            }
            views.setImageViewBitmap(R.id.play_pause_background, context.getColoredBitmapFromColor(R.drawable.widget_rectangle_background, backgroundColor, 52.dp, 52.dp))
            views.setImageViewBitmap(R.id.widget_configure, if (widgetCacheEntry.widget.showConfigure) context.getColoredBitmapFromColor(R.drawable.ic_widget_configure, foregroundColor, 24.dp, 24.dp) else null)
            if (widgetType == WidgetType.PILL) views.setImageViewBitmap(R.id.cover_background, context.getColoredBitmapFromColor(R.drawable.widget_circle, secondaryBackgroundColor, 48.dp, 48.dp))
        }




        if (!forPreview) widgetCacheEntry.currentMedia = service?.currentMediaWrapper
        if (!playing)
            setupTexts(views, context.getString(R.string.widget_default_text), "", context)
        else
            setupTexts(views, widgetCacheEntry.currentMedia?.title, widgetCacheEntry.currentMedia?.artist, context)

        if (widgetCacheEntry.playing != playing || colorChanged) views.setImageViewBitmap(R.id.play_pause, context.getColoredBitmapFromColor(getPlayPauseImage(playing, widgetType), foregroundColor))
        views.setContentDescription(R.id.play_pause, context.getString(if (!playing) R.string.resume_playback_short_title else R.string.pause))

        views.setInt(R.id.player_container_background, "setColorFilter", backgroundColor)
        views.setInt(R.id.player_container_background, "setImageAlpha", (widgetCacheEntry.widget.opacity.toFloat() * 255 / 100).toInt())
        views.setInt(R.id.play_pause_background, "setImageAlpha", (widgetCacheEntry.widget.opacity.toFloat() * 255 / 100).toInt())
        if (!playing) displayCover(playing, widgetType, views, context, widgetCacheEntry)


        //cover

        //set it square on layouts needing it
        val coverPadding = (widgetCacheEntry.widget.height.dp - 48.dp) / 2
        if (BuildConfig.DEBUG) Log.d("AppWidget", "coverPadding: $coverPadding: ${widgetCacheEntry.widget.height} /// ${48.dp}")
        views.setViewPadding(R.id.cover_parent, coverPadding, 0, coverPadding, 0)

        views.setInt(R.id.separator, "setColorFilter", secondaryBackgroundColor)

        if (forPreview) widgetCacheEntry.currentCover = "fake"
        if (forPreview) {
            displayCover(true, widgetType, views, context, widgetCacheEntry)
            views.setImageViewBitmap(R.id.cover, cutBitmapCover(widgetType, previewBitmap!!))
        } else if (playing && widgetCacheEntry.currentMedia?.artworkMrl != widgetCacheEntry.currentCover) {
            widgetCacheEntry.currentCover = widgetCacheEntry.currentMedia?.artworkMrl
            if (!widgetCacheEntry.currentMedia?.artworkMrl.isNullOrEmpty()) {
                if (BuildConfig.DEBUG) Log.d("AppWidget", "Bugfix Refresh - Update cover: $widgetCacheEntry.currentMedia?.artworkMrl for ${widgetCacheEntry.widget.widgetId}")
                runIO {
                    val cover = AudioUtil.readCoverBitmap(Uri.decode(widgetCacheEntry.currentMedia?.artworkMrl), 320)
                    val wm = context.getSystemService<WindowManager>()!!
                    val dm = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
                    runOnMainThread {
                        if (cover != null) {
                            if (cover.byteSize() < dm.widthPixels * dm.heightPixels * 6) {
                                val finalBitmap = cutBitmapCover(widgetType, cover)
                                views.setImageViewBitmap(R.id.cover, finalBitmap)
                                if (widgetCacheEntry.widget.theme == 1) widgetCacheEntry.palette = Palette.from(cover).generate()
                                displayCover(true, widgetType, views, context, widgetCacheEntry)
                            }
                        } else {
                            widgetCacheEntry.palette = null
                            widgetCacheEntry.foregroundColor = null
                            displayCover(false, widgetType, views, context, widgetCacheEntry)
                        }
                        applyUpdate(context, views, partial, appWidgetId)
                    }
                }
            } else {
                widgetCacheEntry.palette = null
                widgetCacheEntry.foregroundColor = null
                displayCover(false, widgetType, views, context, widgetCacheEntry)
            }
        }


        //position
        service?.playlistManager?.player?.progress?.value?.let { progress ->
            val pos = (progress.time.toFloat() / progress.length)
            if (BuildConfig.DEBUG) Log.d("AppWidget", "Refresh - progress updated to $pos // ${progress.length} / ${progress.time} ")
            runIO {
                //generate round progress bar
                when (widgetType) {
                    WidgetType.MICRO -> {
                        val bitmap = widgetCacheEntry.generateCircularProgressbar(context, 128.dp.toFloat(), pos)
                        views.setImageViewBitmap(R.id.progress_round, bitmap)
                        if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                    }
                    WidgetType.PILL -> {
                        widgetCacheEntry.generatePillProgressbar(context, pos)?.let { bitmap ->
                            views.setImageViewBitmap(R.id.progress_round, bitmap)
                        }
                        if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                    }
                    WidgetType.MINI, WidgetType.MACRO -> {
                        val bitmap = widgetCacheEntry.generateCircularProgressbar(context, 42.dp.toFloat(), pos, 3.dp.toFloat())
                        views.setImageViewBitmap(R.id.progress_round, bitmap)
                        if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                    }
                }
            }
        }

        views.setViewVisibility(R.id.progress_round, if (playing) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.forward, if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.backward, if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_forward, if (!hasEnoughSpaceForSeek(widgetCacheEntry, widgetType)) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_forward_text, if (!hasEnoughSpaceForSeek(widgetCacheEntry, widgetType)) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_rewind, if (!hasEnoughSpaceForSeek(widgetCacheEntry, widgetType)) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_rewind_text, if (!hasEnoughSpaceForSeek(widgetCacheEntry, widgetType)) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)

        views.setContentDescription(R.id.seek_rewind, context.getString(R.string.seek_backward_content_description, widgetCacheEntry.widget.rewindDelay.toString()))
        views.setContentDescription(R.id.seek_forward, context.getString(R.string.seek_forward_content_description, widgetCacheEntry.widget.forwardDelay.toString()))


        views.setTextColor(R.id.songName, foregroundColor)
        views.setTextColor(R.id.artist, foregroundColorSecondary)
        views.setTextColor(R.id.seek_forward_text, foregroundColor)
        views.setTextColor(R.id.seek_rewind_text, foregroundColor)
        views.setTextColor(R.id.app_name, foregroundColor)

        views.setTextViewText(R.id.seek_forward_text, widgetCacheEntry.widget.forwardDelay.toString())
        views.setTextViewText(R.id.seek_rewind_text, widgetCacheEntry.widget.rewindDelay.toString())

        widgetCacheEntry.playing = playing

        if (BuildConfig.DEBUG) Log.d("AppWidget", "Layout is ${
            when (views.layoutId) {

                R.layout.widget_pill -> "pill"
                R.layout.widget_micro -> "micro"
                R.layout.widget_macro -> "macro"
                else -> "mini"
            }
        }")
        return views
    }

    /**
     * Check if the widget has enough space to display the seek icons
     *
     * @param widgetCacheEntry the widget cache entry to check the size on
     * @param widgetType the current [WidgetType]
     * @return true if the widget has nough space
     */
    private fun hasEnoughSpaceForSeek(widgetCacheEntry: WidgetCacheEntry, widgetType: WidgetType) = when (widgetType) {
        WidgetType.MINI -> widgetCacheEntry.widget.width.dp > widgetCacheEntry.widget.height.dp + 48.dp * 5
        WidgetType.MACRO -> widgetCacheEntry.widget.width.dp > 48.dp * 5
        else -> false
    }

    private fun cutBitmapCover(widgetType: WidgetType, cover: Bitmap): Bitmap =
            when (widgetType) {
                WidgetType.MICRO -> BitmapUtil.roundBitmap(cover)
                WidgetType.PILL -> BitmapUtil.roundBitmap(cover)
                WidgetType.MINI -> BitmapUtil.roundedRectangleBitmap(cover, bottomRight = false, topRight = false)
                else -> cover
            }

    private fun getFakeMedia(): MediaWrapper? {
        return MLServiceLocator.getAbstractMediaWrapper(
                -1,
                "fakemedia://",
                -1L,
                -1f,
                1337000L,
                MediaWrapper.TYPE_AUDIO,
                "Track name",
                "",
                "Artist name",
                "",
                "",
                "",
                0,
                0,
                "fakemedia://",
                0,
                0,
                0,
                0,
                0L,
                0L,
                true,
                0,
                true
        )
    }

    private fun setupTexts(views: RemoteViews, title: String?, artist: String?, context: Context) {
        if (BuildConfig.DEBUG) Log.d("AppWidget", "setupTexts: $title /// $artist")
        views.setTextViewText(R.id.songName, title)
        views.setTextViewText(R.id.artist, if (!artist.isNullOrBlank()) " ${TextUtils.separator} $artist" else artist)
        if (title == context.getString(R.string.widget_default_text)) {
            views.setViewVisibility(R.id.app_name, View.VISIBLE)
            views.setViewVisibility(R.id.songName, View.GONE)
            views.setViewVisibility(R.id.artist, View.GONE)
            views.setViewVisibility(R.id.seek_rewind_text, View.GONE)
            views.setViewVisibility(R.id.seek_forward_text, View.GONE)
        } else {
            views.setViewVisibility(R.id.app_name, View.GONE)
            views.setViewVisibility(R.id.songName, View.VISIBLE)
            views.setViewVisibility(R.id.artist, View.VISIBLE)
            views.setViewVisibility(R.id.seek_rewind_text, View.VISIBLE)
            views.setViewVisibility(R.id.seek_forward_text, View.VISIBLE)
        }
    }

    private fun displayCover(playing: Boolean, widgetType: WidgetType, views: RemoteViews, context: Context, widgetCacheEntry: WidgetCacheEntry) {
        val foregroundColor = widgetCacheEntry.widget.getForegroundColor(context, palette = widgetCacheEntry.palette)
        if (BuildConfig.DEBUG) Log.d("AppWidget", "Bugfix displayCover: widgetType $widgetType /// playing $playing /// foregroundColor $foregroundColor -> ${java.lang.String.format("#%06X", 0xFFFFFF and foregroundColor)}")
        if (!playing) {
            val iconSize = if (widgetType == WidgetType.PILL) 24.dp else 48.dp
            views.setImageViewBitmap(R.id.app_icon, context.getColoredBitmapFromColor(R.drawable.ic_widget_icon, foregroundColor, iconSize, iconSize))
            views.setViewVisibility(R.id.cover, View.INVISIBLE)
            views.setViewVisibility(R.id.app_icon, View.VISIBLE)
            widgetCacheEntry.currentCover = null
        } else {
            views.setViewVisibility(R.id.app_icon, View.INVISIBLE)
            views.setViewVisibility(R.id.cover, View.VISIBLE)
        }
    }


    override fun onAppWidgetOptionsChanged(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetId: Int, newOptions: Bundle?) {
        if (context == null) return
        val options = appWidgetManager!!.getAppWidgetOptions(appWidgetId)

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if (BuildConfig.DEBUG) Log.d("AppWidget", "New widget size: $minWidth / $minHeight")


        if (appWidgetId != 0) onReceive(context, Intent(ACTION_WIDGET_INIT).apply { putExtra("ID", appWidgetId) })
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }


    companion object {
        const val TAG = "VLC/VLCAppWidgetProvider"
        val ACTION_WIDGET_PREFIX = "widget.mini.".buildPkgString()
        val ACTION_WIDGET_INIT = ACTION_WIDGET_PREFIX + "INIT"
        val ACTION_WIDGET_UPDATE = ACTION_WIDGET_PREFIX + "UPDATE"

        val ACTION_WIDGET_UPDATE_POSITION = ACTION_WIDGET_PREFIX + "UPDATE_POSITION"
        val ACTION_WIDGET_ENABLED = ACTION_WIDGET_PREFIX + "ENABLED"
        val ACTION_WIDGET_DISABLED = ACTION_WIDGET_PREFIX + "DISABLED"
    }

    private fun getPlayPauseImage(isPlaying: Boolean, widgetType: WidgetType): Int {
        return if (isPlaying) if (widgetType == WidgetType.MINI || widgetType == WidgetType.MACRO) R.drawable.ic_widget_pause_inner else R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    }


    private fun applyUpdate(context: Context, views: RemoteViews, partial: Boolean, appWidgetId: Int) {
        if (BuildConfig.DEBUG) Log.d("AppWidget", "Apply update")
        val manager = AppWidgetManager.getInstance(context)
        try {
            if (partial)
                manager.partiallyUpdateAppWidget(appWidgetId, views)
            else
                manager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update widget $appWidgetId", e)
        }
    }

}

fun Bitmap.byteSize(): Int {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
        return allocationByteCount
    }
    return rowBytes * height
}
