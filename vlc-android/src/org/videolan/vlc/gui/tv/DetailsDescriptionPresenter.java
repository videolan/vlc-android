package org.videolan.vlc.gui.tv;

import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.util.Log;

public class DetailsDescriptionPresenter extends
		AbstractDetailsDescriptionPresenter {
	public static final String TAG ="DetailsDescriptionPresenter";

	protected void onBindDescription(ViewHolder viewHolder, Object itemData) {
		Log.d(TAG, "itemData "+itemData);
		MediaItemDetails details = (MediaItemDetails) itemData;
		// In a production app, the itemData object contains the information
		// needed to display details for the media item:
		// viewHolder.getTitle().setText(details.getShortTitle());

		// Here we provide static data for testing purposes:
		viewHolder.getTitle().setText(details.getTitle());
		viewHolder.getSubtitle().setText(details.getSubTitle());
//		viewHolder.getBody().setText(details.getBody());
	}

	
}
