/*****************************************************************************
 * RecommendationsService.java
 *****************************************************************************
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
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
 *****************************************************************************/
package org.videolan.vlc;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

import java.util.Arrays;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class RecommendationsService extends IntentService {

    private static final String TAG = "VLC/RecommendationsService";
    private static final int MAX_RECOMMENDATIONS = 3;

    private NotificationManager mNotificationManager;

    public RecommendationsService() {
        super("RecommendationService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        doRecommendations();
    }

    private void buildRecommendation(MediaWrapper movie, int id, int priority) {
        if (movie == null)
            return;

        // build the recommendation as a Notification object
        Notification notification = new NotificationCompat.BigPictureStyle(
                new NotificationCompat.Builder(RecommendationsService.this)
                        .setContentTitle(movie.getTitle())
                        .setContentText(movie.getDescription())
                        .setContentInfo(getString(R.string.app_name))
                        .setPriority(priority)
                        .setLocalOnly(true)
                        .setOngoing(true)
                        .setColor(ContextCompat.getColor(this, R.color.orange800))
                        .setCategory(Notification.CATEGORY_RECOMMENDATION)
                        .setLargeIcon(BitmapUtil.getPicture(movie))
                        .setSmallIcon(R.drawable.icon)
                        .setContentIntent(buildPendingIntent(movie, id))
        ).build();

        // post the recommendation to the NotificationManager
        mNotificationManager.notify(id, notification);
    }

    private PendingIntent buildPendingIntent(MediaWrapper mediaWrapper, int id) {
        Intent intent = new Intent(RecommendationsService.this, VideoPlayerActivity.class);
        intent.setAction(Constants.PLAY_FROM_VIDEOGRID);
        intent.putExtra(Constants.PLAY_EXTRA_ITEM_LOCATION, mediaWrapper.getUri());
        intent.putExtra(Constants.PLAY_EXTRA_ITEM_TITLE, mediaWrapper.getTitle());
        intent.putExtra(Constants.PLAY_EXTRA_FROM_START, false);

        return PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void doRecommendations() {
        mNotificationManager.cancelAll();
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                int id = 0;
                List<MediaWrapper> videoList = Arrays.asList(VLCApplication.getMLInstance().getRecentVideos());
                if (Util.isListEmpty(videoList))
                    return;
                for (MediaWrapper mediaWrapper : videoList){
                    buildRecommendation(mediaWrapper, ++id, Notification.PRIORITY_DEFAULT);
                    if (id == MAX_RECOMMENDATIONS)
                        break;
                }
            }
        });
    }
}
