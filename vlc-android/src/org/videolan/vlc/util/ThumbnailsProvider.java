package org.videolan.vlc.util;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.media.MediaGroup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.videolan.medialibrary.Medialibrary.THUMBS_FOLDER_NAME;
import static org.videolan.vlc.gui.helpers.AudioUtil.readCoverBitmap;

public class ThumbnailsProvider {

    private static final String TAG = "VLC/ThumbnailsProvider";

    private static final int sImageWidth = VLCApplication.getAppResources().getDimensionPixelSize(VLCApplication.showTvUi() ? R.dimen.tv_grid_card_thumb_width : R.dimen.grid_card_thumb_width);
    private static final int MAX_IMAGES = 4;

    @WorkerThread
    public static Bitmap getMediaThumbnail(final MediaWrapper item) {
        if (item.getType() == MediaWrapper.TYPE_GROUP)
            return ThumbnailsProvider.getComposedImage((MediaGroup) item);
        if (item.getType() == MediaWrapper.TYPE_VIDEO && TextUtils.isEmpty(item.getArtworkMrl()))
            return getVideoThumbnail(item.getUri().getPath());
        else
            return AudioUtil.readCoverBitmap(Uri.decode(item.getArtworkMrl()), sImageWidth);
    }

    @WorkerThread
    private static Bitmap getVideoThumbnail(final String filePath) {
        final File appDir = VLCApplication.getAppContext().getExternalFilesDir(null);
        final boolean hasCache = appDir != null && appDir.exists();
        final String thumbPath = hasCache ? appDir.getAbsolutePath()+ THUMBS_FOLDER_NAME
                +"/"+ FileUtils.getFileNameFromPath(filePath)+".jpg" : null;
        final Bitmap cacheBM = hasCache ? BitmapCache.getInstance().getBitmapFromMemCache(thumbPath) : null;
        if (cacheBM != null)
            return cacheBM;
        if (hasCache && new File(thumbPath).exists())
            return readCoverBitmap(thumbPath, sImageWidth);
        final Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND);
        if (bitmap != null) {
            if (hasCache)
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        saveOnDisk(bitmap, thumbPath);
                    }
                });
        }
        return bitmap;
    }

    @WorkerThread
    public static Bitmap getComposedImage(MediaGroup group) {
        final BitmapCache bmc = BitmapCache.getInstance();
        final String key = "group:"+group.getTitle();
        Bitmap composedImage = bmc.getBitmapFromMemCache(key);
        if (composedImage == null) {
            composedImage = composeImage(group);
            if (composedImage != null)
                bmc.addBitmapToMemCache(key, composedImage);
        }
        return composedImage;
    }
    /**
     * Compose 1 image from combined media thumbnails
     * @param group The MediaGroup instance
     * @return a Bitmap object
     */
    private static Bitmap composeImage(MediaGroup group) {
        final Bitmap[] sourcesImages = new Bitmap[Math.min(MAX_IMAGES, group.size())];
        int count = 0, minWidth = Integer.MAX_VALUE, minHeight = Integer.MAX_VALUE;
        for (MediaWrapper media : group.getAll()) {
            final Bitmap bm = readCoverBitmap(Uri.decode(media.getArtworkMrl()), sImageWidth);
            if (bm != null) {
                int width = bm.getWidth();
                int height = bm.getHeight();
                sourcesImages[count++] = bm;
                minWidth = Math.min(minWidth, width);
                minHeight = Math.min(minHeight, height);
                if (count == MAX_IMAGES)
                    break;
            }
        }
        if (count == 0)
            return null;
        if (count == 1)
            return sourcesImages[0];
        return composeCanvas(sourcesImages, count, minWidth, minHeight);
    }

    private static Bitmap composeCanvas(Bitmap[] sourcesImages, int count, int minWidth, int minHeight) {
        int overlayWidth, overlayHeight;
        switch (count) {
            case 4:
                overlayWidth = 2*minWidth;
                overlayHeight = 2*minHeight;
                break;
            default:
                overlayWidth = minWidth;
                overlayHeight = minHeight;
                break;
        }
        Bitmap bmOverlay = Bitmap.createBitmap(overlayWidth, overlayHeight, sourcesImages[0].getConfig());

        Canvas canvas = new Canvas(bmOverlay);
        switch (count) {
            case 2:
                for (int i = 0; i < count; ++i)
                    sourcesImages[i] = BitmapUtil.centerCrop(sourcesImages[i], minWidth/2, minHeight);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth / 2, 0, null);
                break;
            case 3:
                sourcesImages[0] = BitmapUtil.centerCrop(sourcesImages[0], minWidth/2, minHeight/2);
                sourcesImages[1] = BitmapUtil.centerCrop(sourcesImages[1], minWidth/2, minHeight/2);
                sourcesImages[2] = BitmapUtil.centerCrop(sourcesImages[2], minWidth, minHeight/2);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth / 2, 0, null);
                canvas.drawBitmap(sourcesImages[2], 0, minHeight/2, null);
                break;
            case 4:
                for (int i = 0; i < count; ++i)
                    sourcesImages[i] = BitmapUtil.centerCrop(sourcesImages[i], minWidth, minHeight);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth , 0, null);
                canvas.drawBitmap(sourcesImages[2], 0, minHeight, null);
                canvas.drawBitmap(sourcesImages[3], minWidth , minHeight, null);
                break;
        }
        return bmOverlay;
    }

    private static void saveOnDisk(Bitmap bitmap, String destPath) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        final byte[] byteArray = stream.toByteArray();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(destPath);
            fos.write(byteArray);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            Util.close(fos);
            Util.close(stream);
        }
    }
}
