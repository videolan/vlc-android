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

import org.videolan.vlc.AudioService;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class VLCAppWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "VLC/VLCAppWidgetProvider";

    public static String ACTION_WIDGET_BACKWARD = "org.videolan.vlc.widget.Backward";
    public static String ACTION_WIDGET_PLAY = "org.videolan.vlc.widget.Play";
    public static String ACTION_WIDGET_STOP = "org.videolan.vlc.widget.Stop";
    public static String ACTION_WIDGET_FORWARD = "org.videolan.vlc.widget.Forward";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        context.startService(new Intent(context, AudioService.class));
    }
}
