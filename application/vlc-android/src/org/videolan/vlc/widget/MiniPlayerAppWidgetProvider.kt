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
import androidx.annotation.DrawableRes
import androidx.core.content.getSystemService
import androidx.palette.graphics.Palette
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.getColoredBitmapFromColor
import org.videolan.vlc.media.Progress
import org.videolan.vlc.repository.WidgetRepository
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.getPendingIntent
import org.videolan.vlc.widget.utils.*
import org.videolan.vlc.widget.utils.WidgetUtils.getWidgetType
import org.videolan.vlc.widget.utils.WidgetUtils.shouldShowSeek
import java.util.*


class MiniPlayerAppWidgetProvider : AppWidgetProvider() {
    private lateinit var _widgetRepository: WidgetRepository
    private val enableLogs = true
    private val logTypeFilter = arrayListOf(WidgetLogType.BITMAP_GENERATION, WidgetLogType.INFO)
    private val logWidgetIdFilter = arrayListOf<Int>()

    /**
     * Retrieve the [WidgetRepository]
     *
     * @param context the context to use
     * @return the [WidgetRepository]
     */
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
        log(-1, WidgetLogType.INFO, "onReceive: ${intent.action}")
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

    /**
     * Generated the [RemoteViews] depending on the widget configuration and sizing
     *
     * @param context the context to use
     * @param appWidgetId the widget id
     * @param intent the intent triggering this layout pass
     * @param forPreview is this layout pass for a preview (in the configuration activity)
     * @param previewBitmap if this is for a preview, the [Bitmap] to use as a cover
     * @param previewPalette if this is for a preview, the [Palette] to use
     * @return the [RemoteViews] to send to the app widget host
     */
    suspend fun layoutWidget(context: Context, appWidgetId: Int, intent: Intent, forPreview: Boolean = false, previewBitmap: Bitmap? = null, previewPalette: Palette? = null, previewPlaying:Boolean = false): RemoteViews? {

        val partial = ACTION_WIDGET_INIT != intent.action
        log(appWidgetId, WidgetLogType.INFO, "layoutWidget widget id $appWidgetId / partial: $partial / action = ${intent.action}")

        val widgetRepository = getWidgetRepository(context)
        val persitedWidget = widgetRepository.getWidget(appWidgetId)
                ?: return null

        val widgetCacheEntry = if (forPreview) WidgetCacheEntry(persitedWidget, getFakeMedia()) else WidgetCache.getEntry(persitedWidget)
                ?: WidgetCache.addEntry(persitedWidget)
        if (!partial && !forPreview) widgetCacheEntry.reset()


        val palette: Palette? = if (forPreview) previewPalette else widgetCacheEntry.palette
        //val foregroundColorSecondary = widgetCacheEntry.widget.getForegroundColor(context, true, palette = palette)
        val foregroundColor = widgetCacheEntry.widget.getForegroundColor(context, palette = palette)
        val backgroundColor = widgetCacheEntry.widget.getBackgroundColor(context, palette = palette)
//        val secondaryBackgroundColor = widgetCacheEntry.widget.getBackgroundColor(context, palette = palette, secondary = true)

        val service = PlaybackService.serviceFlow.value
        val playing = (service?.isPlaying == true && !forPreview) || previewPlaying
        val colorChanged = !partial || widgetCacheEntry.foregroundColor != foregroundColor || (widgetCacheEntry.widget.theme == 1 && widgetCacheEntry.playing != playing)
        widgetCacheEntry.foregroundColor = foregroundColor


        //determine layout
        val oldType = getWidgetType(widgetCacheEntry.widget)

        val size = WidgetSizeUtil.getWidgetsSize(context, appWidgetId)
        if (size.first != 0 && size.second != 0 && (widgetCacheEntry.widget.width != size.first || widgetCacheEntry.widget.height != size.second)) {
            widgetCacheEntry.widget.width = size.first
            widgetCacheEntry.widget.height = size.second
            widgetCacheEntry.currentCoverInvalidated = true
            log(appWidgetId, WidgetLogType.INFO, "Updating widget entry to: $widgetCacheEntry.widget")

            widgetRepository.updateWidget(widgetCacheEntry.widget, true)
        }
        val correctedSize = if (size.first == 0 && size.second == 0) {
            log(appWidgetId, WidgetLogType.INFO, "Size is 0. Getting size from db: $widgetCacheEntry.widget")
            Pair(widgetCacheEntry.widget.width, widgetCacheEntry.widget.height)
        } else size
        log(appWidgetId, WidgetLogType.INFO, "New widget size by provider: ${correctedSize.first} / ${correctedSize.second} // ratio = ${correctedSize.first.toFloat() / correctedSize.second}")
        val widgetType = getWidgetType(widgetCacheEntry.widget)


        val views = RemoteViews(context.packageName, widgetType.layout)
        if (oldType != widgetType) {
            WidgetCache.clear(widgetCacheEntry.widget)
            applyUpdate(context, views, false, appWidgetId)
        }

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
        val piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piConfigure = PendingIntent.getActivity(context, 0, iConfigure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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

        if (colorChanged) {
            if (widgetType == WidgetType.MACRO) views.setImageViewBitmap(R.id.cover_background, context.getColoredBitmapFromColor(R.drawable.widget_rectangle_background, widgetCacheEntry.widget.getBackgroundColor(context, palette), widgetCacheEntry.widget.width.dp,  widgetCacheEntry.widget.width.dp))
            log(appWidgetId, WidgetLogType.BITMAP_GENERATION, "Bugfix Color changed!!! for widget $appWidgetId // forPreview $forPreview // foreground color: ${java.lang.String.format("#%06X", 0xFFFFFF and foregroundColor)} /// palette ${widgetCacheEntry.palette}")
            if (android.text.TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
                views.setImageViewBitmap(R.id.forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_previous_normal, foregroundColor))
                views.setImageViewBitmap(R.id.backward, context.getColoredBitmapFromColor(R.drawable.ic_widget_next_normal, foregroundColor))
                views.setImageViewBitmap(R.id.seek_rewind, context.getColoredBitmapFromColor(R.drawable.ic_widget_forward_10, foregroundColor))
                views.setImageViewBitmap(R.id.seek_forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_rewind_10, foregroundColor))
            } else {
                views.setImageViewBitmap(R.id.forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_next_normal, foregroundColor))
                views.setImageViewBitmap(R.id.backward, context.getColoredBitmapFromColor(R.drawable.ic_widget_previous_normal, foregroundColor))
                views.setImageViewBitmap(R.id.seek_rewind, context.getColoredBitmapFromColor(R.drawable.ic_widget_rewind_10, foregroundColor))
                views.setImageViewBitmap(R.id.seek_forward, context.getColoredBitmapFromColor(R.drawable.ic_widget_forward_10, foregroundColor))
            }
            views.setImageViewBitmap(R.id.play_pause_background, context.getColoredBitmapFromColor(R.drawable.widget_rectangle_background, widgetCacheEntry.widget.getBackgroundSecondaryColor(context, palette = palette), 52.dp, 52.dp))
            views.setImageViewBitmap(R.id.widget_configure, if (widgetCacheEntry.widget.showConfigure) context.getColoredBitmapFromColor(R.drawable.ic_widget_configure, foregroundColor, 24.dp, 24.dp) else null)
            if (widgetType == WidgetType.PILL) views.setImageViewBitmap(R.id.cover_background, context.getColoredBitmapFromColor(R.drawable.widget_circle, widgetCacheEntry.widget.getSeparatorColor(context), 48.dp, 48.dp))

            val rippleDrawable = if (backgroundColor.isLight()) R.drawable.widget_touch_background else R.drawable.widget_touch_background_dark
            arrayOf(R.id.play_pause, R.id.forward, R.id.backward, R.id.seek_rewind, R.id.seek_forward, R.id.widget_configure).forEach {
                views.setInt(it, "setBackgroundResource", rippleDrawable)
            }
        }



        val settings = Settings.getInstance(context)

        if (!forPreview) widgetCacheEntry.currentMedia = service?.currentMediaWrapper
        val title = when {
            forPreview -> if (!previewPlaying)context.getString(R.string.widget_default_text) else widgetCacheEntry.currentMedia?.title
            playing -> service?.title ?: widgetCacheEntry.currentMedia?.title
            else -> settings.getString(
                KEY_CURRENT_AUDIO_RESUME_TITLE,
                context.getString(R.string.widget_default_text)
            )
        }

        val artist = when {
            forPreview -> widgetCacheEntry.currentMedia?.artist
            playing -> service?.artist ?: widgetCacheEntry.currentMedia?.artist
            else -> settings.getString(KEY_CURRENT_AUDIO_RESUME_ARTIST, "")
        }
        setupTexts(context, views, widgetType, title, artist)

        if (widgetCacheEntry.playing != playing || colorChanged) views.setImageViewBitmap(R.id.play_pause, context.getColoredBitmapFromColor(getPlayPauseImage(playing, widgetType), foregroundColor))
        views.setContentDescription(R.id.play_pause, context.getString(if (!playing) R.string.resume_playback_short_title else R.string.pause))

        views.setInt(R.id.player_container_background, "setColorFilter", backgroundColor)
        views.setInt(R.id.player_container_background, "setImageAlpha", (widgetCacheEntry.widget.opacity.toFloat() * 255 / 100).toInt())
        views.setViewPadding(R.id.player_container_background, 1.dp, 1.dp, 1.dp, 1.dp)
        views.setInt(R.id.play_pause_background, "setImageAlpha", (widgetCacheEntry.widget.opacity.toFloat() * 255 / 100).toInt())
        //cover

        //set it square on layouts needing it
        val coverPadding = (widgetCacheEntry.widget.height.dp - 48.dp) / 2
        log(appWidgetId, WidgetLogType.INFO, "coverPadding: $coverPadding: ${widgetCacheEntry.widget.height} /// ${48.dp}")
        views.setViewPadding(R.id.cover_parent, coverPadding, 0, coverPadding, 0)

        views.setInt(R.id.separator, "setBackgroundColor", widgetCacheEntry.widget.getSeparatorColor(context))
        views.setInt(R.id.separator, "setImageAlpha", (widgetCacheEntry.widget.opacity.toFloat() * 255 / 100).toInt())

        if (forPreview && previewPlaying) widgetCacheEntry.currentCover = "fake"
        if (forPreview) {
            displayCover(context, views, true, widgetType, widgetCacheEntry)
            views.setImageViewBitmap(R.id.cover, cutBitmapCover(widgetType, previewBitmap!!, widgetCacheEntry))
        } else if (widgetCacheEntry.currentMedia?.artworkMrl != widgetCacheEntry.currentCover || widgetCacheEntry.currentCoverInvalidated) {
            widgetCacheEntry.currentCoverInvalidated = false
            widgetCacheEntry.currentCover = if (service?.isVideoPlaying == false) widgetCacheEntry.currentMedia?.artworkMrl
                    ?: settings.getString(KEY_CURRENT_AUDIO_RESUME_THUMB, null) else settings.getString(KEY_CURRENT_AUDIO_RESUME_THUMB, null)
            if (!widgetCacheEntry.currentCover.isNullOrEmpty()) {
                log(appWidgetId, WidgetLogType.INFO, "Bugfix Refresh - Update cover: ${widgetCacheEntry.currentMedia?.artworkMrl} for ${widgetCacheEntry.widget.widgetId}")
                runIO {
                    log(appWidgetId, WidgetLogType.BITMAP_GENERATION, "Generating cover")
                    val cover = AudioUtil.readCoverBitmap(Uri.decode(widgetCacheEntry.currentCover), 320)
                    val wm = context.getSystemService<WindowManager>()!!
                    val dm = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
                    runOnMainThread {
                        if (cover != null) {
                            if (cover.byteSize() < dm.widthPixels * dm.heightPixels * 6) {
                                val finalBitmap = cutBitmapCover(widgetType, cover, widgetCacheEntry)
                                views.setImageViewBitmap(R.id.cover, finalBitmap)
                                if (widgetCacheEntry.widget.theme == 1) widgetCacheEntry.palette = Palette.from(cover).generate()
                                displayCover(context, views, true, widgetType, widgetCacheEntry)
                            }
                        } else {
                            widgetCacheEntry.palette = null
                            widgetCacheEntry.foregroundColor = null
                            displayCover(context, views, false, widgetType, widgetCacheEntry)
                        }
                        applyUpdate(context, views, partial, appWidgetId)
                    }
                }
            } else {
                widgetCacheEntry.palette = null
                widgetCacheEntry.foregroundColor = null
                displayCover(context, views, false, widgetType, widgetCacheEntry)
            }
        }

        if (!playing && widgetCacheEntry.currentCover == null) displayCover(context, views, playing, widgetType, widgetCacheEntry)


        //position
        (service?.playlistManager?.player?.progress?.value ?: if (forPreview) Progress(3333L, 10000L) else null)?.let { progress ->
            val pos = (progress.time.toFloat() / progress.length)
            log(appWidgetId, WidgetLogType.BITMAP_GENERATION, "Refresh - progress updated to $pos // ${progress.length} / ${progress.time} ")
            runIO {
                //generate round progress bar
                when (widgetType) {
                    WidgetType.MICRO -> {
                        val bitmap = widgetCacheEntry.generateCircularProgressbar(context, 128.dp.toFloat(), pos)
                        runOnMainThread {
                            views.setImageViewBitmap(R.id.progress_round, bitmap)
                            if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                        }
                    }
                    WidgetType.PILL -> {
                        widgetCacheEntry.generatePillProgressbar(context, pos)?.let { bitmap ->
                            runOnMainThread {
                                views.setImageViewBitmap(R.id.progress_round, bitmap)
                                if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                            }
                        }
                    }
                    WidgetType.MINI, WidgetType.MACRO -> {
                        val bitmap = widgetCacheEntry.generateCircularProgressbar(context, 32.dp.toFloat(), pos, 3.dp.toFloat())
                        runOnMainThread {
                            views.setImageViewBitmap(R.id.progress_round, bitmap)
                            if (!forPreview) applyUpdate(context, views, partial, appWidgetId)
                        }
                    }
                }
            }
        }

        val showSeek = shouldShowSeek(widgetCacheEntry.widget, widgetType)
        log(appWidgetId, WidgetLogType.INFO, "hasEnoughSpaceForSeek: $showSeek")
        views.setViewVisibility(R.id.progress_round, if (playing) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.forward, if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.backward, if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_forward, if (!showSeek) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_forward_text, if (!showSeek) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_rewind, if (!showSeek) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.seek_rewind_text, if (!showSeek) View.GONE else if (playing) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.widget_left_space, if (!showSeek) View.VISIBLE else if (playing) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_right_space, if (!showSeek) View.VISIBLE else if (playing) View.GONE else View.VISIBLE)

        views.setContentDescription(R.id.seek_rewind, context.getString(R.string.seek_backward_content_description, widgetCacheEntry.widget.rewindDelay.toString()))
        views.setContentDescription(R.id.seek_forward, context.getString(R.string.seek_forward_content_description, widgetCacheEntry.widget.forwardDelay.toString()))


        views.setTextColor(R.id.songName, foregroundColor)
        views.setTextColor(R.id.artist, widgetCacheEntry.widget.getArtistColor(context, palette))
        views.setTextColor(R.id.seek_forward_text, foregroundColor)
        views.setTextColor(R.id.seek_rewind_text, foregroundColor)
        views.setTextColor(R.id.app_name, foregroundColor)

        views.setTextViewText(R.id.seek_forward_text, widgetCacheEntry.widget.forwardDelay.toString())
        views.setTextViewText(R.id.seek_rewind_text, widgetCacheEntry.widget.rewindDelay.toString())

        widgetCacheEntry.playing = playing

        log(appWidgetId, WidgetLogType.INFO, "Layout is ${
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
     * Cuts the cover bitmap depending on the [WidgetType]
     *
     * @param widgetType the widget's [WidgetType]
     * @param cover the cover [Bitmap]
     * @param widgetCacheEntry the [WidgetCacheEntry] used for the [Bitmap] sizing
     * @return a cut [Bitmap]
     */
    private fun cutBitmapCover(widgetType: WidgetType, cover: Bitmap, widgetCacheEntry: WidgetCacheEntry): Bitmap =
            when (widgetType) {
                WidgetType.MICRO -> BitmapUtil.roundBitmap(cover)
                WidgetType.PILL -> BitmapUtil.roundBitmap(cover)
                WidgetType.MINI -> BitmapUtil.roundedRectangleBitmap(cover, widgetCacheEntry.widget.height.dp, bottomRight = false, topRight = false)
                WidgetType.MACRO -> BitmapUtil.roundedRectangleBitmap(cover, widgetCacheEntry.widget.width.dp)
            }

    /**
     * Retrieve a fake media to be displayed in the widget's preview
     *
     * @return a fake [MediaWrapper]
     */
    private fun getFakeMedia(): MediaWrapper {
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
                false,
                0,
                true,
                1683711438317L
        )
    }

    /**
     * Setup the title and artist texts and their views visibility
     *
     * @param context the context to use
     * @param views the [RemoteViews] to set the texts into
     * @param widgetType the widget type
     * @param title the track name
     * @param artist the artist name
     */
    private fun setupTexts(context: Context, views: RemoteViews, widgetType: WidgetType, title: String?, artist: String?) {
        log(-1, WidgetLogType.INFO, "setupTexts: $title /// $artist")
        views.setTextViewText(R.id.songName, title)
        views.setTextViewText(R.id.artist, if (!artist.isNullOrBlank()) "${if (widgetType == WidgetType.MACRO) "" else " ${TextUtils.separator} "}$artist" else artist)
        if (title == context.getString(R.string.widget_default_text)) {
            views.setViewVisibility(R.id.app_name, View.VISIBLE)
            views.setViewVisibility(R.id.songName, View.GONE)
            views.setViewVisibility(R.id.artist, View.GONE)
        } else {
            views.setViewVisibility(R.id.app_name, View.GONE)
            views.setViewVisibility(R.id.songName, View.VISIBLE)
            views.setViewVisibility(R.id.artist, View.VISIBLE)
        }
    }

    /**
     * Setup the cover for the [RemoteViews] and the visibility
     *
     * @param context the context to use
     * @param views the [RemoteViews] in which to display the cover
     * @param playing is the playback currently active
     * @param widgetType the [WidgetType] the widget uses
     * @param widgetCacheEntry the [WidgetCacheEntry] used for the colors
     */
    private fun displayCover(context: Context, views: RemoteViews, playing: Boolean, widgetType: WidgetType, widgetCacheEntry: WidgetCacheEntry) {
        if (widgetType == WidgetType.MINI && !widgetCacheEntry.widget.showCover) {
            views.setViewVisibility(R.id.app_icon, View.GONE)
            views.setViewVisibility(R.id.cover, View.GONE)
            views.setViewVisibility(R.id.cover_background, View.GONE)
            views.setViewVisibility(R.id.cover_parent, View.GONE)
            views.setViewVisibility(R.id.separator, View.GONE)

            return
        }
        val foregroundColor = widgetCacheEntry.widget.getForegroundColor(context, palette = widgetCacheEntry.palette)
        log(widgetCacheEntry.widget.widgetId, WidgetLogType.INFO, "Bugfix displayCover: widgetType $widgetType /// playing $playing /// foregroundColor $foregroundColor -> ${java.lang.String.format("#%06X", 0xFFFFFF and foregroundColor)}")
        if (!playing) {
            val iconSize = when (widgetType) {
                WidgetType.PILL -> 24.dp
                WidgetType.MACRO -> 128.dp
                else -> 48.dp
            }
            views.setImageViewBitmap(R.id.app_icon, context.getColoredBitmapFromColor(R.drawable.ic_widget_icon, foregroundColor, iconSize, iconSize))
            views.setViewVisibility(R.id.cover, View.INVISIBLE)
            views.setViewVisibility(R.id.app_icon, View.VISIBLE)
            views.setViewVisibility(R.id.separator, View.VISIBLE)
            views.setViewVisibility(R.id.cover_parent, View.VISIBLE)

            widgetCacheEntry.currentCover = null
        } else {
            views.setViewVisibility(R.id.app_icon, View.INVISIBLE)
            views.setViewVisibility(R.id.cover, View.VISIBLE)
            views.setViewVisibility(R.id.separator, View.VISIBLE)
            views.setViewVisibility(R.id.cover_parent, View.VISIBLE)
        }
    }


    override fun onAppWidgetOptionsChanged(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetId: Int, newOptions: Bundle?) {
        if (context == null) return
        val options = appWidgetManager!!.getAppWidgetOptions(appWidgetId)

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        log(appWidgetId, WidgetLogType.INFO, "New widget size: $minWidth / $minHeight")


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

    /**
     * Get the play/pause drawable depending on the playing state and the [WidgetType]
     *
     * @param isPlaying is the playback currently active
     * @param widgetType the [WidgetType] to use
     * @return the drawable to use for the play/pause icon
     */
    @DrawableRes
    private fun getPlayPauseImage(isPlaying: Boolean, widgetType: WidgetType) = if (isPlaying) if (widgetType == WidgetType.MINI || widgetType == WidgetType.MACRO) R.drawable.ic_widget_pause_inner else R.drawable.ic_widget_pause else R.drawable.ic_widget_play

    /**
     * Applies the update to the widget
     *
     * @param context the [Context] to use
     * @param views the [RemoteViews] to display
     * @param partial is this a partial update?
     * @param appWidgetId the widget's id to update
     */
    private fun applyUpdate(context: Context, views: RemoteViews?, partial: Boolean, appWidgetId: Int) {
        log(appWidgetId, WidgetLogType.INFO, "Apply update. Widget id: $appWidgetId Partial: $partial")
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

    /**
     * Widget logger
     *
     * @param widgetId the widget id to filter / display
     * @param logType the logType to filter / display
     * @param text the log text
     */
    private fun log(widgetId:Int, logType:WidgetLogType, text:String) {
        if (!enableLogs || !BuildConfig.DEBUG) return
        if (logTypeFilter.contains(logType) && (logWidgetIdFilter.isEmpty() || logWidgetIdFilter.contains(widgetId) || widgetId == -1)) Log.i("VLCAppWidget", "$logType - $widgetId - $text")
    }

    enum class WidgetLogType {
        INFO, BITMAP_GENERATION
    }


}

/**
 * Get a [Bitmap] size in bytes
 *
 * @return the [Bitmap] size in bytes
 */
fun Bitmap.byteSize(): Int {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
        return allocationByteCount
    }
    return rowBytes * height
}
