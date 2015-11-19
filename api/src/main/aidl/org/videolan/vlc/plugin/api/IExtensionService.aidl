package org.videolan.vlc.plugin.api;

import org.videolan.vlc.plugin.api.IExtensionHost;
import org.videolan.vlc.plugin.api.VLCExtensionItem;

interface IExtensionService {
    // Protocol version 1
    oneway void onInitialize(in IExtensionHost host);
    oneway void browse(int intId, String stringId);
}
