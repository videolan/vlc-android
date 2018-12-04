package org.videolan.vlc.gui.helpers;


import android.content.Context;
import android.content.Intent;

import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.WorkersKt;

public class MedialibraryUtils {

    public static void removeDir(final String path) {
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                VLCApplication.getMLInstance().removeFolder(path);
            }
        });
    }

    public static void addDir(final String path) {
        addDir(path, VLCApplication.getAppContext());
    }

    public static void addDir(final String path, Context context) {
        final Intent intent = new Intent(Constants.ACTION_DISCOVER, null, context, MediaParsingService.class);
        intent.putExtra(Constants.EXTRA_PATH, path);
        context.startService(intent);
    }

    public static void addDevice(final String path, Context context) {
        final Intent intent = new Intent(Constants.ACTION_DISCOVER_DEVICE, null, context, MediaParsingService.class);
        intent.putExtra(Constants.EXTRA_PATH, path);
        context.startService(intent);
    }
}
