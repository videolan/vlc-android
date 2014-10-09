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
