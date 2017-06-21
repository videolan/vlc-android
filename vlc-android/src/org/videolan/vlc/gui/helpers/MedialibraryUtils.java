package org.videolan.vlc.gui.helpers;


import android.content.Intent;

import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.VLCApplication;

public class MedialibraryUtils {

    public static void removeDir(final String path) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                VLCApplication.getMLInstance().removeFolder(path);
            }
        });
    }

    public static void addDir(final String path) {
        Intent intent = new Intent(MediaParsingService.ACTION_DISCOVER, null, VLCApplication.getAppContext(), MediaParsingService.class);
        intent.putExtra(MediaParsingService.EXTRA_PATH, path);
        VLCApplication.getAppContext().startService(intent);
    }
}
