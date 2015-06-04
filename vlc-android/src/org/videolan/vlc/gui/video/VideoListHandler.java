package org.videolan.vlc.gui.video;

import android.os.Message;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.WeakHandler;

public class VideoListHandler extends WeakHandler<IVideoBrowser> {
    public static final int UPDATE_ITEM = 0;
    public static final int MEDIA_ITEMS_UPDATED = 100;

    public VideoListHandler(IVideoBrowser owner) {
        super(owner);
    }

    @Override
    public void handleMessage(Message msg) {
        IVideoBrowser owner = getOwner();
        if(owner == null) return;

        switch (msg.what) {
            case UPDATE_ITEM:
                owner.updateItem();
                break;
            case MediaLibrary.MEDIA_ITEMS_UPDATED:
                owner.updateList();
                break;
        }
    }
};