/*****************************************************************************
 * RecommendationsService.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors, VideoLAN and VideoLabs
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
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.WeakHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class RecommendationsService extends IntentService {

    private static final String TAG = "VLC/RecommendationsService";
    private static final int NUM_RECOMMANDATIONS = 10;

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
        if (intent != null) {
            final String action = intent.getAction();
            MediaLibrary.getInstance().addUpdateHandler(mHandler);
        }
    }
    private static Notification buildRecommendation(Context context, Media movie)
            throws IOException {

        if (sNotificationManager == null) {
            sNotificationManager = (NotificationManager)
                    sContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }

        Bundle extras = new Bundle();
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
//                        .setColor(Color.BLUE)
                        .setCategory("recommendation")
                        .setLargeIcon(sMediaDatabase.getPicture(sContext, movie.getLocation()))
                        .setSmallIcon(R.drawable.icon)
                        .setContentIntent(buildPendingIntent(movie))
//                        .setExtras(extras))
        ).build();

        // post the recommendation to the NotificationManager
        sNotificationManager.notify(0, notification);
        sNotificationManager = null;
        return notification;
    }

    private static PendingIntent buildPendingIntent(Media media) {

        Intent intent = new Intent(sContext, VideoPlayerActivity.class);
        intent.setAction(VideoPlayerActivity.PLAY_FROM_VIDEOGRID);
        intent.putExtra("itemLocation", media.getLocation());
        intent.putExtra("itemTitle", media.getTitle());
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
            ArrayList<Media> videoList = MediaLibrary.getInstance().getVideoItems();
            ArrayList<Media> videos = new ArrayList<Media>(videoList.size());
            Bitmap pic;
            for (Media media : videoList){
                pic = sMediaDatabase.getPicture(sContext, media.getLocation());
                if (pic != null && pic.getByteCount() > 4 && media.getTime() == 0) {
                    videos.add(media);
                }
            }
            if (!videos.isEmpty())
                Collections.shuffle(videos);
            videoList = null;
            int size = Math.min(NUM_RECOMMANDATIONS, videos.size());
            for (int i = 0 ; i < size ; ++i) {
                try {
                    buildRecommendation(sContext, videos.get(i));
                } catch (IOException e) {
                    Log.e(TAG, "failed notif for "+ videos.get(i).getTitle(), e);
                }
            }
        }
    }
}
