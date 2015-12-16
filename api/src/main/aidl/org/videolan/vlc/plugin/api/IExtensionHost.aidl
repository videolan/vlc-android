package org.videolan.vlc.plugin.api;

import org.videolan.vlc.plugin.api.VLCExtensionItem;
import android.net.Uri;

interface IExtensionHost {
    // Protocol version 1
    oneway void updateList(in String title, in List<VLCExtensionItem> items, boolean showParams);
    oneway void playUri(in Uri uri, String title);
    oneway void unBind(int index);
}
