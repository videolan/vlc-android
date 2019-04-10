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
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.util.AndroidDevices;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import static org.videolan.vlc.util.Constants.ACTION_PAUSE_SCAN;
import static org.videolan.vlc.util.Constants.ACTION_RESUME_SCAN;
import static org.videolan.vlc.util.Util.getMediaDescription;

public class NotificationHelper {
    public final static String TAG = "VLC/NotificationHelper";

    private final static StringBuilder sb = new StringBuilder();
    public static final String VLC_DEBUG_CHANNEL = "vlc_debug";

    public static Notification createPlaybackNotification(Context ctx, boolean video, String title, String artist,
                                                          String album, Bitmap cover, boolean playing, boolean pausable,
                                                          MediaSessionCompat.Token sessionToken,
                                                          PendingIntent spi) {

        final PendingIntent piStop = MediaButtonReceiver.buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_STOP);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "vlc_playback");
        sb.setLength(0);
        sb.append(title).append(" - ").append(artist);
        builder.setSmallIcon(video ? R.drawable.ic_notif_video : R.drawable.ic_notif_audio)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setContentText(getMediaDescription(artist, album))
                .setLargeIcon(cover)
                .setTicker(sb.toString())
                .setAutoCancel(!playing)
                .setOngoing(playing)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setDeleteIntent(piStop)
                .setContentIntent(spi)
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_widget_previous_w, ctx.getString(R.string.previous),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(ctx,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
        if (pausable) {
            if (playing) builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_widget_pause_w, ctx.getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(ctx,
                            PlaybackStateCompat.ACTION_PLAY_PAUSE)));
            else builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_widget_play_w, ctx.getString(R.string.play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(ctx,
                            PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        }
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_widget_next_w, ctx.getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(ctx,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        if (!pausable) builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_widget_close_w, ctx.getString(R.string.stop), piStop));

        if (AndroidDevices.showMediaStyle) {
            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(piStop)
            );
        }
        return builder.build();
    }

    private static NotificationCompat.Builder scanCompatBuilder;
    private static final Intent notificationIntent = new Intent();

    public static Notification createScanNotification(Context ctx, String progressText, boolean updateActions, boolean paused) {
        if (scanCompatBuilder == null) {
            scanCompatBuilder = new NotificationCompat.Builder(ctx, "vlc_medialibrary")
                    .setContentIntent(PendingIntent.getActivity(ctx, 0, new Intent(ctx, StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                    .setSmallIcon(R.drawable.ic_notif_scan)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(ctx.getString(R.string.ml_scanning))
                    .setAutoCancel(false)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setOngoing(true);
        }
        scanCompatBuilder.setContentText(progressText);

        if (updateActions) {
            notificationIntent.setAction(paused ? ACTION_RESUME_SCAN : ACTION_PAUSE_SCAN);
            final PendingIntent pi = PendingIntent.getBroadcast(ctx.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            final NotificationCompat.Action playpause = paused ? new NotificationCompat.Action(R.drawable.ic_play, ctx.getString(R.string.resume), pi)
                    : new NotificationCompat.Action(R.drawable.ic_pause, ctx.getString(R.string.pause), pi);
            scanCompatBuilder.mActions.clear();
            scanCompatBuilder.addAction(playpause);
        }
        return scanCompatBuilder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannels(Context appCtx) {
        final NotificationManager notificationManager = (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
        // Playback channel
        CharSequence name = appCtx.getString(R.string.playback);
        String description = appCtx.getString(R.string.playback_controls);
        NotificationChannel channel = new NotificationChannel("vlc_playback", name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        // Scan channel
        name = appCtx.getString(R.string.medialibrary_scan);
        description = appCtx.getString(R.string.Medialibrary_progress);
        channel = new NotificationChannel("vlc_medialibrary", name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);

        // Misc channel
        name = appCtx.getString(R.string.misc);
        channel = new NotificationChannel("misc", name, NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);

        // Recommendations channel
        if (AndroidDevices.isAndroidTv) {
            name = appCtx.getString(R.string.recommendations);
            description = appCtx.getString(R.string.recommendations_desc);
            channel = new NotificationChannel("vlc_recommendations", name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createDebugServcieChannel(Context appCtx) {
        final NotificationManager notificationManager = (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;
        // Playback channel
        final CharSequence name = appCtx.getString(R.string.debug_logs);
        final NotificationChannel channel = new NotificationChannel(VLC_DEBUG_CHANNEL, name, NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
    }
}
