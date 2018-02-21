package org.videolan.vlc.gui.helpers;


import android.content.Intent;
import android.net.Uri;

import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.Constants;

public class MedialibraryUtils {

    public static void removeDir(final String path) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                VLCApplication.getMLInstance().removeFolder(Uri.decode(path));
            }
        });
    }

    public static void addDir(final String path) {
        final Intent intent = new Intent(Constants.ACTION_DISCOVER, null, VLCApplication.getAppContext(), MediaParsingService.class);
        intent.putExtra(Constants.EXTRA_PATH, Uri.decode(path));
        VLCApplication.getAppContext().startService(intent);
    }
}
