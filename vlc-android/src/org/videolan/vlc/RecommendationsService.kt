/*****************************************************************************
 * RecommendationsService.kt
 *
 * Copyright © 2014-2018 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.vlc

import android.annotation.TargetApi
import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.*

private const val TAG = "VLC/RecommendationsService"
private const val MAX_RECOMMENDATIONS = 3

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class RecommendationsService : IntentService("RecommendationService"), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

    private lateinit var mNotificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        mNotificationManager = getAppSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onHandleIntent(intent: Intent?) {
        doRecommendations()
    }

    private fun buildRecommendation(mw: MediaWrapper?, id: Int, priority: Int) {
        if (mw == null) return
        // build the recommendation as a Notification object
        val notification = NotificationCompat.BigPictureStyle(
                NotificationCompat.Builder(this@RecommendationsService, "vlc_recommendations")
                        .setContentTitle(mw.title)
                        .setContentText(mw.description)
                        .setContentInfo(getString(R.string.app_name))
                        .setPriority(priority)
                        .setLocalOnly(true)
                        .setOngoing(true)
                        .setColor(ContextCompat.getColor(this, R.color.orange800))
                        .setCategory(Notification.CATEGORY_RECOMMENDATION)
                        .setLargeIcon(BitmapUtil.getPicture(mw))
                        .setSmallIcon(R.drawable.icon)
                        .setContentIntent(buildPendingIntent(mw, id))
        ).build()
        // post the recommendation to the NotificationManager
        mNotificationManager.notify(id, notification)
    }

    private fun buildPendingIntent(mw: MediaWrapper, id: Int): PendingIntent {
        val intent = Intent(this@RecommendationsService, VideoPlayerActivity::class.java)
        intent.action = PLAY_FROM_VIDEOGRID
        intent.putExtra(PLAY_EXTRA_ITEM_LOCATION, mw.uri)
        intent.putExtra(PLAY_EXTRA_ITEM_TITLE, mw.title)
        intent.putExtra(PLAY_EXTRA_FROM_START, false)
        return PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun doRecommendations() = launch {
        mNotificationManager.cancelAll()
        val videoList = withContext(Dispatchers.IO) { VLCApplication.getMLInstance().recentVideos }
        if (Util.isArrayEmpty(videoList)) return@launch
        for ((id, mediaWrapper) in videoList.withIndex()) {
            buildRecommendation(mediaWrapper, id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            if (id == MAX_RECOMMENDATIONS) break
        }
    }
}
