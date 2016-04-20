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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.util.Strings;

import java.util.Locale;

abstract public class VLCAppWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "VLC/VLCAppWidgetProvider";
    public static final String ACTION_REMOTE_BACKWARD = Strings.buildPkgString("remote.Backward");
    public static final String ACTION_REMOTE_PLAYPAUSE = Strings.buildPkgString("remote.PlayPause");
    public static final String ACTION_REMOTE_STOP = Strings.buildPkgString("remote.Stop");
    public static final String ACTION_REMOTE_FORWARD = Strings.buildPkgString("remote.Forward");
    public static final String ACTION_WIDGET_PREFIX = Strings.buildPkgString("widget.");
    public static final String ACTION_WIDGET_INIT = ACTION_WIDGET_PREFIX+"INIT";
    public static final String ACTION_WIDGET_UPDATE = ACTION_WIDGET_PREFIX+"UPDATE";
    public static final String ACTION_WIDGET_UPDATE_COVER = ACTION_WIDGET_PREFIX+"UPDATE_COVER";
    public static final String ACTION_WIDGET_UPDATE_POSITION = ACTION_WIDGET_PREFIX+"UPDATE_POSITION";

    public static final String VLC_PACKAGE = BuildConfig.APPLICATION_ID;

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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!action.startsWith(ACTION_WIDGET_PREFIX)) {
            super.onReceive(context, intent);
            return;
        }

        RemoteViews views = new RemoteViews(BuildConfig.APPLICATION_ID, getLayout());
        boolean partial = AndroidUtil.isHoneycombOrLater();

        if (ACTION_WIDGET_INIT.equals(action) || !partial) {
            /* commands */
            Intent iBackward = new Intent(ACTION_REMOTE_BACKWARD);
            Intent iPlay = new Intent(ACTION_REMOTE_PLAYPAUSE);
            Intent iStop = new Intent(ACTION_REMOTE_STOP);
            Intent iForward = new Intent(ACTION_REMOTE_FORWARD);
            Intent iVlc = new Intent(VLCApplication.getAppContext(), MainActivity.class);

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
            if (AndroidUtil.isJellyBeanMR1OrLater() && TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
                boolean black = this instanceof VLCAppWidgetProviderBlack;
                views.setImageViewResource(R.id.forward, black ? R.drawable.ic_widget_previous_w : R.drawable.ic_widget_previous);
                views.setImageViewResource(R.id.backward, black ? R.drawable.ic_widget_next_w : R.drawable.ic_widget_next);
            }
        }

        if (ACTION_WIDGET_UPDATE.equals(action)) {
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            boolean isplaying = intent.getBooleanExtra("isplaying", false);

            views.setTextViewText(R.id.songName, title);
            views.setTextViewText(R.id.artist, artist);
            views.setImageViewResource(R.id.play_pause, getPlayPauseImage(isplaying));
        }
        else if (ACTION_WIDGET_UPDATE_COVER.equals(action)) {
            Bitmap cover = intent.getParcelableExtra("cover");
            if (cover != null)
                views.setImageViewBitmap(R.id.cover, cover);
            else
                views.setImageViewResource(R.id.cover, R.drawable.icon);
            views.setProgressBar(R.id.timeline, 100, 0, false);
        }
        else if (ACTION_WIDGET_UPDATE_POSITION.equals(action)) {
            float pos = intent.getFloatExtra("position", 0f);
            views.setProgressBar(R.id.timeline, 100, (int) (100 * pos), false);
        }

        ComponentName widget = new ComponentName(context, this.getClass());
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (partial)
            manager.partiallyUpdateAppWidget(manager.getAppWidgetIds(widget), views);
        else
            manager.updateAppWidget(widget, views);
    }

    abstract protected int getLayout();

    abstract protected int getPlayPauseImage(boolean isPlaying);

}
