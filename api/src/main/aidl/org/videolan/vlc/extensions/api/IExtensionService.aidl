package org.videolan.vlc.extensions.api;

import org.videolan.vlc.extensions.api.IExtensionHost;
import org.videolan.vlc.extensions.api.VLCExtensionItem;

interface IExtensionService {
    // Protocol version 1
    oneway void onInitialize(int index, in IExtensionHost host);
    oneway void browse(int intId, String stringId); // longId?
    oneway void refresh();
}
