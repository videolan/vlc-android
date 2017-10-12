/*****************************************************************************
 * NotificationHelper.java
 *****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.helpers;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

import static org.videolan.vlc.util.Constants.ACTION_PAUSE_SCAN;
import static org.videolan.vlc.util.Constants.ACTION_RESUME_SCAN;
import static org.videolan.vlc.util.Util.getMediaDescription;

public class NotificationHelper {
    public final static String TAG = "VLC/NotificationHelper";

    public static Notification createPlaybackNotification(Context ctx, boolean video, String title, String artist,
                                                          String album, Bitmap cover, boolean playing,
                                                          MediaSessionCompat.Token sessionToken,
                                                          PendingIntent spi) {

        final PendingIntent piStop = PendingIntent.getBroadcast(ctx, 0, new Intent(Constants.ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent piBackward = PendingIntent.getBroadcast(ctx, 0, new Intent(Constants.ACTION_REMOTE_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent piPlay = PendingIntent.getBroadcast(ctx, 0, new Intent(Constants.ACTION_REMOTE_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent piForward = PendingIntent.getBroadcast(ctx, 0, new Intent(Constants.ACTION_REMOTE_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT);
        if (AndroidUtil.isOOrLater) {
            final Notification.Builder builder = new Notification.Builder(ctx, "vlc_playback");
            builder.setSmallIcon(video ? R.drawable.ic_notif_video : R.drawable.ic_notif_audio)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(Util.getMediaDescription(artist, album))
                    .setLargeIcon(cover)
                    .setTicker(title + " - " + artist)
                    .setAutoCancel(!playing)
                    .setOngoing(playing)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setDeleteIntent(piStop)
                    .setContentIntent(spi)
                    .addAction(R.drawable.ic_widget_previous_w, ctx.getString(R.string.previous), piBackward);
            if (playing)
                builder.addAction(R.drawable.ic_widget_pause_w, ctx.getString(R.string.pause), piPlay);
            else
                builder.addAction(R.drawable.ic_widget_play_w, ctx.getString(R.string.play), piPlay);
            builder.addAction(R.drawable.ic_widget_next_w, ctx.getString(R.string.next), piForward);
            builder.addAction(R.drawable.ic_widget_close_w, ctx.getString(R.string.stop), piStop);

            final Notification.MediaStyle ms = new Notification.MediaStyle()
                    .setShowActionsInCompactView(0,1,2);
            if (sessionToken != null)
                ms.setMediaSession((MediaSession.Token) sessionToken.getToken());
            builder.setStyle(ms);
            return builder.build();
        } else {
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
            builder.setSmallIcon(video ? R.drawable.ic_notif_video : R.drawable.ic_notif_audio)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(getMediaDescription(artist, album))
                    .setLargeIcon(cover)
                    .setTicker(title + " - " + artist)
                    .setAutoCancel(!playing)
                    .setOngoing(playing)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setDeleteIntent(piStop)
                    .setContentIntent(spi)
                    .addAction(R.drawable.ic_widget_previous_w, ctx.getString(R.string.previous), piBackward);
            if (playing)
                builder.addAction(R.drawable.ic_widget_pause_w, ctx.getString(R.string.pause), piPlay);
            else
                builder.addAction(R.drawable.ic_widget_play_w, ctx.getString(R.string.play), piPlay);
            builder.addAction(R.drawable.ic_widget_next_w, ctx.getString(R.string.next), piForward);
            builder.addAction(R.drawable.ic_widget_close_w, ctx.getString(R.string.stop), piStop);
            if (AndroidDevices.showMediaStyle) {
                builder.setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(sessionToken)
                        .setShowActionsInCompactView(0,1,2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(piStop)
                );
            }
            return builder.build();
        }
    }

    private static android.support.v4.app.NotificationCompat.Builder scanCompatBuilder;
    private static Notification.Builder scanBuilder;
    private static final Intent notificationIntent = new Intent();
    public static Notification createScanNotification(Context ctx, String progressText, boolean updateActions, boolean paused) {
        if (AndroidUtil.isOOrLater) {
            if (scanBuilder == null) {
                scanBuilder = new Notification.Builder(ctx, "vlc_medialibrary")
                        .setContentIntent(PendingIntent.getActivity(ctx, 0, new Intent(ctx, StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                        .setSmallIcon(R.drawable.ic_notif_scan)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentTitle(VLCApplication.getAppResources().getString(R.string.ml_scanning))
                        .setAutoCancel(false)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setOngoing(true);
            }
            scanBuilder.setContentText(progressText);

            if (updateActions) {
                notificationIntent.setAction(paused ? ACTION_RESUME_SCAN : ACTION_PAUSE_SCAN);
                final PendingIntent pi = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                final Notification.Action playpause = paused ? new Notification.Action(R.drawable.ic_play, VLCApplication.getAppResources().getString(R.string.resume), pi)
                        : new Notification.Action(R.drawable.ic_pause, VLCApplication.getAppResources().getString(R.string.pause), pi);
                scanBuilder.setActions(playpause);
            }
            return scanBuilder.build();

        } else {
            if (scanCompatBuilder == null) {
                scanCompatBuilder = new NotificationCompat.Builder(ctx)
                        .setContentIntent(PendingIntent.getActivity(ctx, 0, new Intent(ctx, StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                        .setSmallIcon(R.drawable.ic_notif_scan)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentTitle(VLCApplication.getAppResources().getString(R.string.ml_scanning))
                        .setAutoCancel(false)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setOngoing(true);
            }
            scanCompatBuilder.setContentText(progressText);

            if (updateActions) {
                notificationIntent.setAction(paused ? ACTION_RESUME_SCAN : ACTION_PAUSE_SCAN);
                final PendingIntent pi = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                final NotificationCompat.Action playpause = paused ? new NotificationCompat.Action(R.drawable.ic_play, VLCApplication.getAppResources().getString(R.string.resume), pi)
                        : new NotificationCompat.Action(R.drawable.ic_pause, VLCApplication.getAppResources().getString(R.string.pause), pi);
                scanCompatBuilder.mActions.clear();
                scanCompatBuilder.addAction(playpause);
            }
            return scanCompatBuilder.build();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannels() {
        final NotificationManager notificationManager = (NotificationManager) VLCApplication.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final Resources res = VLCApplication.getAppResources();
        // Playback channel
        CharSequence name = res.getString(R.string.playback);
        String description = res.getString(R.string.playback_controls);
        NotificationChannel channel = new NotificationChannel("vlc_playback", name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        // Scan channel
        name = res.getString(R.string.medialibrary_scan);
        description = res.getString(R.string.Medialibrary_progress);
        channel = new NotificationChannel("vlc_medialibrary", name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        // Recommendations channel
        if (AndroidDevices.isAndroidTv) {
            name = res.getString(R.string.recommendations);
            description = res.getString(R.string.recommendations_desc);
            channel = new NotificationChannel("vlc_recommendations", name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
