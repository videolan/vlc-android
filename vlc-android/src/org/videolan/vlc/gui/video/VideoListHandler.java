package org.videolan.vlc.gui.video;

import android.os.Message;

import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.WeakHandler;

public class VideoListHandler extends WeakHandler<IVideoBrowser> {

    public VideoListHandler(IVideoBrowser owner) {
        super(owner);
    }

    @Override
    public void handleMessage(Message msg) {
        IVideoBrowser owner = getOwner();
        if(owner == null) return;

        switch (msg.what) {
            case MediaLibrary.UPDATE_ITEM:
                owner.updateItem((MediaWrapper)msg.obj);
                break;
            case MediaLibrary.MEDIA_ITEMS_UPDATED:
                owner.updateList();
                break;
        }
    }
};