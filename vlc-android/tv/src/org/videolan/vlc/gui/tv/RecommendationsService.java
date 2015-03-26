/*****************************************************************************
 * RecommendationsService.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.vlc.gui.tv;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.WeakHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class RecommendationsService extends IntentService {

    private static final String TAG = "VLC/RecommendationsService";
    private static final int NUM_RECOMMANDATIONS = 4;

    private static NotificationManager sNotificationManager;
    private static MediaDatabase sMediaDatabase = MediaDatabase.getInstance();
    private static Context sContext;

    public RecommendationsService() {
        super("RecommendationsService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && !doRecommendations()) {
            MediaLibrary.getInstance().addUpdateHandler(mHandler);
            MediaLibrary.getInstance().loadMediaItems();
        }
    }
    private static void buildRecommendation(MediaWrapper movie, int id) {
        if (movie == null)
            return;

        if (sNotificationManager == null) {
            sNotificationManager = (NotificationManager)
                    sContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }

        //TODO
//        if (mBackgroundUri != movie.getBackgroundUri()) {
//            extras.putString(EXTRA_BACKGROUND_IMAGE_URL, movie.getBackgroundUri());
//        }

        // build the recommendation as a Notification object
        Notification notification = new NotificationCompat.BigPictureStyle(
                new NotificationCompat.Builder(sContext)
                        .setContentTitle(movie.getTitle())
                        .setContentText(movie.getDescription())
                        .setContentInfo("VLC")
//                        .setSortKey("0.8")
                        .setPriority(7)
                        .setLocalOnly(true)
                        .setOngoing(true)
                        .setColor(sContext.getResources().getColor(R.color.orange500))
                        .setCategory("recommendation")
                        .setLargeIcon(sMediaDatabase.getPicture(sContext, movie.getLocation()))
                        .setSmallIcon(R.drawable.icon)
                        .setContentIntent(buildPendingIntent(movie))
        ).build();

        // post the recommendation to the NotificationManager
        sNotificationManager.notify(id, notification);
        sNotificationManager = null;
    }

    private static PendingIntent buildPendingIntent(MediaWrapper MediaWrapper) {
        Intent intent = new Intent(sContext, VideoPlayerActivity.class);
        intent.setAction(VideoPlayerActivity.PLAY_FROM_VIDEOGRID);
        intent.putExtra("itemLocation", MediaWrapper.getLocation());
        intent.putExtra("itemTitle", MediaWrapper.getTitle());
        intent.putExtra("dontParse", false);
        intent.putExtra("fromStart", false);
        intent.putExtra("itemPosition", -1);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(sContext);
        stackBuilder.addParentStack(VideoPlayerActivity.class);
        stackBuilder.addNextIntent(intent);

        PendingIntent pi = stackBuilder.getPendingIntent(
                0, PendingIntent.FLAG_UPDATE_CURRENT);
        return pi;
    }

    RecommendationsHandler mHandler = new RecommendationsHandler(this);

    private static class RecommendationsHandler extends WeakHandler<RecommendationsService> {
        public RecommendationsHandler(RecommendationsService owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            doRecommendations();
        }
    }

    private static boolean doRecommendations() {
        String last = Uri.decode(PreferenceManager.getDefaultSharedPreferences(sContext).getString(PreferencesActivity.VIDEO_LAST, null));
        int id = 0;
        if (last != null) {
            buildRecommendation(MediaLibrary.getInstance().getMediaItem(last), id);
        }
        ArrayList<MediaWrapper> videoList = MediaLibrary.getInstance().getVideoItems();
        if (videoList == null || videoList.isEmpty())
            return false;
        Bitmap pic;
        Collections.shuffle(videoList);
        for (MediaWrapper mediaWrapper : videoList){
            if (TextUtils.equals(mediaWrapper.getLocation(), last))
                continue;
            pic = sMediaDatabase.getPicture(sContext, mediaWrapper.getLocation());
            if (pic != null && pic.getByteCount() > 4 && mediaWrapper.getTime() == 0) {
                buildRecommendation(mediaWrapper, ++id);
            }
            if (id == 3)
                return true;
        }
        return false;
    }
}
