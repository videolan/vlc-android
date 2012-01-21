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

package org.videolan.vlc.android.widget;

import org.videolan.vlc.android.AudioPlayer;
import org.videolan.vlc.android.AudioServiceController;
import org.videolan.vlc.android.MainActivity;
import org.videolan.vlc.android.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class VLCAppWidgetProvider extends AppWidgetProvider {
    public static final String TAG = "VLC/VLCAppWidgetProvider";
	
	public static String ACTION_WIDGET_BACKWARD = "ActionReceiverBack";
	public static String ACTION_WIDGET_PLAY = "ActionReceiverPlaypause";
	public static String ACTION_WIDGET_STOP = "ActionReceiverStop";
	public static String ACTION_WIDGET_FORWARD = "ActionReceiverForward";
	private RemoteViews views = new RemoteViews("org.videolan.vlc.android", R.layout.vlcwidget);
	private AudioServiceController controller = AudioServiceController.getInstance();
	
	@Override
	public void onEnabled(Context context) {
		AppWidgetManager a = AppWidgetManager.getInstance(context);
		this.onUpdate(context, a, a.getAppWidgetIds(new ComponentName(context, org.videolan.vlc.android.widget.VLCAppWidgetProvider.class)));
		super.onEnabled(context);
	}
	
	@Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ComponentName cn = new ComponentName(context, VLCAppWidgetProvider.class);
        appWidgetManager.updateAppWidget(cn, buildUpdate(context, appWidgetIds));
    }

    private RemoteViews buildUpdate(Context context, int[] appWidgetIds) {
        Intent iBackward = new Intent(context, VLCAppWidgetProvider.class);
        iBackward.setAction(ACTION_WIDGET_BACKWARD);
        Intent iPlay = new Intent(context, VLCAppWidgetProvider.class);
        iPlay.setAction(ACTION_WIDGET_PLAY);
        Intent iStop = new Intent(context, VLCAppWidgetProvider.class);
        iStop.setAction(ACTION_WIDGET_STOP);
        Intent iForward = new Intent(context, VLCAppWidgetProvider.class);
        iForward.setAction(ACTION_WIDGET_FORWARD);
        
        PendingIntent piBackward = PendingIntent.getBroadcast(context, 0, iBackward, 0);
        PendingIntent piPlay = PendingIntent.getBroadcast(context, 0, iPlay, 0);
        PendingIntent piStop = PendingIntent.getBroadcast(context, 0, iStop, 0);
        PendingIntent piForward = PendingIntent.getBroadcast(context, 0, iForward, 0);
        
        views.setOnClickPendingIntent(R.id.backward, piBackward);
        views.setOnClickPendingIntent(R.id.play_pause, piPlay);
        views.setOnClickPendingIntent(R.id.stop, piStop);
        views.setOnClickPendingIntent(R.id.forward, piForward);

        return views;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
    	final String action = intent.getAction();
    	if(action.equals(ACTION_WIDGET_BACKWARD)) {
        	controller.previous();
        } else if(action.equals(ACTION_WIDGET_STOP)) {
        	controller.stop();
        	views.setImageViewResource(R.id.play_pause, R.drawable.ic_play);
        } else if(action.equals(ACTION_WIDGET_FORWARD)) {
        	controller.next();
        } else if(action.equals(ACTION_WIDGET_PLAY)) {
        	if(controller.isPlaying()) {
        		controller.pause();
        		views.setImageViewResource(R.id.play_pause, R.drawable.ic_play);
        	} else {
        		if(!controller.hasMedia()) {
        	    	/* Causes a NDK crash, don't un-comment yet
        	    	 * This is because the LibVLC pointer is not initalised properly */
        			/*updateTexts();
        	    	ComponentName cn = new ComponentName(context, VLCAppWidgetProvider.class);  
        	        AppWidgetManager.getInstance(context).updateAppWidget(cn, views);
        			Intent x = new Intent(context, MainActivity.class);
        			x.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        			context.startActivity(x);
        			return;*/
                	Toast.makeText(context, "No media loaded, open VLC first", Toast.LENGTH_SHORT).show();
        		} else {
        			controller.play();
        			views.setImageViewResource(R.id.play_pause, R.drawable.ic_pause);
        		}
        	}
        }
    	updateTexts();
    	ComponentName cn = new ComponentName(context, VLCAppWidgetProvider.class);  
        AppWidgetManager.getInstance(context).updateAppWidget(cn, views);
        super.onReceive(context, intent);  
    }
    
    private void updateTexts() {
    	Log.d(TAG, "Updating texts in widget");
    	if(controller.hasMedia()) {
    		views.setTextViewText(R.id.songName, controller.getTitle());
    		views.setTextViewText(R.id.artist, controller.getArtist());
    		if(controller.isPlaying()) {
    			views.setImageViewResource(R.id.play_pause, R.drawable.ic_pause);
    		} else {
    			views.setImageViewResource(R.id.play_pause, R.drawable.ic_play);
    		}
    	} else {
    		views.setTextViewText(R.id.songName, "VLC mini player");
    		views.setTextViewText(R.id.artist, "");
    		views.setImageViewResource(R.id.play_pause, R.drawable.ic_play);
    	}
    }
    
}
