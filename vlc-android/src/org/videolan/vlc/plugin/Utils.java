package org.videolan.vlc.plugin;

import android.net.Uri;

import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.plugin.api.VLCExtensionItem;

public class Utils {

    public static MediaWrapper mediawrapperFromExtension(VLCExtensionItem vlcItem) {
                MediaWrapper media = new MediaWrapper(Uri.parse(vlcItem.link));
                media.setTitle(vlcItem.title);
                if (vlcItem.type != VLCExtensionItem.TYPE_OTHER_FILE)
                    media.setType(vlcItem.type);
        return media;

    }
}
