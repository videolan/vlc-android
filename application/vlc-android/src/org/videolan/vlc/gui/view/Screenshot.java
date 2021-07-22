package org.videolan.vlc.gui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.File;

public class Screenshot {

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static String capture(Context context, String videoPath, int videoLength) {
        File videoFile = new File(videoPath);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoFile.getAbsolutePath());
        String n = String.valueOf(videoLength);
        Bitmap bitmap = retriever.getFrameAtTime(videoLength * 1000);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, n, "video screenshot");
        return path;
    }
}
