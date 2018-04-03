package org.videolan.vlc.gui.helpers;


import android.content.Intent;

import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.WorkersKt;

public class MedialibraryUtils {

    public static void removeDir(final String path) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                VLCApplication.getMLInstance().removeFolder(path);
            }
        });
    }

    public static void addDir(final String path) {
        final Intent intent = new Intent(Constants.ACTION_DISCOVER, null, VLCApplication.getAppContext(), MediaParsingService.class);
        intent.putExtra(Constants.EXTRA_PATH, path);
        VLCApplication.getAppContext().startService(intent);
    }
}
