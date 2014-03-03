/*****************************************************************************
 * VLCAppWidgetProvider.java
 *****************************************************************************
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
 *****************************************************************************/

package org.videolan.vlc.widget;

import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.R;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

public class VLCAppWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "VLC/VLCAppWidgetProvider";
    public static final String START_FROM_NOTIFICATION = "from_notification";
    public static final String ACTION_REMOTE_BACKWARD = "org.videolan.vlc.remote.Backward";
    public static final String ACTION_REMOTE_PLAYPAUSE = "org.videolan.vlc.remote.PlayPause";
    public static final String ACTION_REMOTE_STOP = "org.videolan.vlc.remote.Stop";
    public static final String ACTION_REMOTE_FORWARD = "org.videolan.vlc.remote.Forward";
    public static final String ACTION_WIDGET_PREFIX = "org.videolan.vlc.widget.";
    public static final String ACTION_WIDGET_INIT = "org.videolan.vlc.widget.INIT";
    public static final String ACTION_WIDGET_UPDATE = "org.videolan.vlc.widget.UPDATE";
    public static final String ACTION_WIDGET_UPDATE_COVER = "org.videolan.vlc.widget.UPDATE_COVER";
    public static final String ACTION_WIDGET_UPDATE_POSITION = "org.videolan.vlc.widget.UPDATE_POSITION";

    public static final String VLC_PACKAGE = "org.videolan.vlc";
    public static final String VLC_SERVICE = "org.videolan.vlc.AudioService";
    public static final String VLC_PLAYER = "org.videolan.vlc.gui.audio.AudioPlayerActivity";
    public static final String VLC_MAIN = "org.videolan.vlc.gui.MainActivity";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        /* init widget */
        Intent i = new Intent(ACTION_WIDGET_INIT);
        onReceive(context, i);

        /* ask a refresh from the service if there is one */
        i = new Intent(ACTION_WIDGET_INIT);
        i.setPackage(VLC_PACKAGE);
        context.sendBroadcast(i);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!action.startsWith(ACTION_WIDGET_PREFIX)) {
            super.onReceive(context, intent);
            return;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.vlcwidget);
        boolean partial = LibVlcUtil.isHoneycombOrLater();

        if (ACTION_WIDGET_INIT.equals(action) || !partial) {
            /* commands */
            Intent iBackward = new Intent(ACTION_REMOTE_BACKWARD);
            Intent iPlay = new Intent(ACTION_REMOTE_PLAYPAUSE);
            Intent iStop = new Intent(ACTION_REMOTE_STOP);
            Intent iForward = new Intent(ACTION_REMOTE_FORWARD);
            Intent iVlc = new Intent();
            iVlc.setClassName(VLC_PACKAGE, VLC_MAIN);
            iVlc.putExtra(START_FROM_NOTIFICATION, true);

            PendingIntent piBackward = PendingIntent.getBroadcast(context, 0, iBackward, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piPlay = PendingIntent.getBroadcast(context, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piStop = PendingIntent.getBroadcast(context, 0, iStop, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piForward = PendingIntent.getBroadcast(context, 0, iForward, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT);

            views.setOnClickPendingIntent(R.id.backward, piBackward);
            views.setOnClickPendingIntent(R.id.play_pause, piPlay);
            views.setOnClickPendingIntent(R.id.stop, piStop);
            views.setOnClickPendingIntent(R.id.forward, piForward);
            views.setOnClickPendingIntent(R.id.cover, piVlc);
            partial = false;
        }

        if (ACTION_WIDGET_UPDATE.equals(action)) {
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            boolean isplaying = intent.getBooleanExtra("isplaying", false);

            views.setTextViewText(R.id.songName, title);
            views.setTextViewText(R.id.artist, artist);
            views.setImageViewResource(R.id.play_pause, isplaying ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
            views.setViewVisibility(R.id.timeline_parent, artist != null && artist.length() > 0 ? View.VISIBLE : View.INVISIBLE);
        }
        else if (ACTION_WIDGET_UPDATE_COVER.equals(action)) {
            Bitmap cover = intent.getParcelableExtra("cover");
            if (cover != null)
                views.setImageViewBitmap(R.id.cover, cover);
            else
                views.setImageViewResource(R.id.cover, R.drawable.cone);
            views.setProgressBar(R.id.timeline, 100, 0, false);
        }
        else if (ACTION_WIDGET_UPDATE_POSITION.equals(action)) {
            float pos = intent.getFloatExtra("position", 0f);
            views.setProgressBar(R.id.timeline, 100, (int) (100 * pos), false);
        }

        ComponentName widget = new ComponentName(context, VLCAppWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (partial)
            manager.partiallyUpdateAppWidget(manager.getAppWidgetIds(widget), views);
        else
            manager.updateAppWidget(widget, views);
    }
}
