package org.videolan.vlc.extensions;

import android.net.Uri;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.vlc.extensions.api.VLCExtensionItem;

public class Utils {

    public static AMediaWrapper mediawrapperFromExtension(VLCExtensionItem vlcItem) {
                AMediaWrapper media = MLServiceLocator.getAMediaWrapper(Uri.parse(vlcItem.link));
                media.setDisplayTitle(vlcItem.title);
                if (vlcItem.type != VLCExtensionItem.TYPE_OTHER_FILE)
                    media.setType(vlcItem.type);
        return media;

    }
}
