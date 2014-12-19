/*****************************************************************************
 * TvUtil.java
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

import org.videolan.libvlc.Media;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.support.v17.leanback.widget.Row;

public class TvUtil {


	public static void openMedia(Activity activity, Media media, Row row){
		if (media.getType() == Media.TYPE_VIDEO){
			VideoPlayerActivity.start(activity, media.getLocation(), false);
		} else if (media.getType() == Media.TYPE_AUDIO){
			Intent intent = new Intent(activity,
					DetailsActivity.class);
			// pass the item information
			intent.putExtra("item", (Parcelable)new MediaItemDetails(media.getTitle(), media.getArtist(), media.getAlbum(), media.getLocation()));
			activity.startActivity(intent);
		} else if (media.getType() == Media.TYPE_GROUP){
			Intent intent = new Intent(activity, VerticalGridActivity.class);
			intent.putExtra("id", row.getId());
			activity.startActivity(intent);
		}
	}
}
