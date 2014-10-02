package org.videolan.vlc.gui.video;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.util.WeakHandler;

import android.os.Message;

public class VideoListHandler extends WeakHandler<VideoBrowserInterface> {
	public static final int UPDATE_ITEM = 0;
    public static final int MEDIA_ITEMS_UPDATED = 100;

	public VideoListHandler(VideoBrowserInterface owner) {
		super(owner);
	}

	@Override
	public void handleMessage(Message msg) {
		VideoBrowserInterface owner = getOwner();
		if(owner == null) return;

		switch (msg.what) {
		case UPDATE_ITEM:
			owner.updateItem();
			break;
		case MediaLibrary.MEDIA_ITEMS_UPDATED:
			owner.updateList();
		}
	}
};