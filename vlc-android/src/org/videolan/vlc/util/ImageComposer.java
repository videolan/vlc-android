/*****************************************************************************
 * ImageComposer.java
 *****************************************************************************
 * Copyright © 2017 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.media.MediaGroup;

public class ImageComposer {

    private static final String TAG = "VLC/ImageComposer";

    private static final int sImageWidth = VLCApplication.getAppResources().getDimensionPixelSize(VLCApplication.showTvUi() ? R.dimen.tv_grid_card_thumb_width : R.dimen.grid_card_thumb_width);
    private static final int MAX_IMAGES = 4;

    /**
     * Compose 1 image from combined media thumbnails
     * @param group The MediaGroup instance
     * @return a Bitmap object
     */
    public static Bitmap composeImage(MediaGroup group) {
        Bitmap[] sourcesImages = new Bitmap[MAX_IMAGES];
        int count = 0, minWidth = Integer.MAX_VALUE, minHeight = Integer.MAX_VALUE;
        for (MediaWrapper media : group.getAll()) {
            Bitmap bm = AudioUtil.readCoverBitmap(Uri.decode(media.getArtworkMrl()), sImageWidth);
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
                    sourcesImages[i] = centerCrop(sourcesImages[i], minWidth/2, minHeight);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth / 2, 0, null);
                break;
            case 3:
                sourcesImages[0] = centerCrop(sourcesImages[0], minWidth/2, minHeight/2);
                sourcesImages[1] = centerCrop(sourcesImages[1], minWidth/2, minHeight/2);
                sourcesImages[2] = centerCrop(sourcesImages[2], minWidth, minHeight/2);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth / 2, 0, null);
                canvas.drawBitmap(sourcesImages[2], 0, minHeight/2, null);
                break;
            case 4:
                for (int i = 0; i < count; ++i)
                    sourcesImages[i] = centerCrop(sourcesImages[i], minWidth, minHeight);
                canvas.drawBitmap(sourcesImages[0], 0, 0, null);
                canvas.drawBitmap(sourcesImages[1], minWidth , 0, null);
                canvas.drawBitmap(sourcesImages[2], 0, minHeight, null);
                canvas.drawBitmap(sourcesImages[3], minWidth , minHeight, null);
                break;
        }
        return bmOverlay;
    }

    private static Bitmap centerCrop(Bitmap srcBmp, int width, int height) {
        Bitmap dstBmp;
        int widthDiff = srcBmp.getWidth()-width;
        int heightDiff = srcBmp.getHeight()-height;
        if (widthDiff == 0 && heightDiff == 0)
            return srcBmp;
        dstBmp = Bitmap.createBitmap(
                srcBmp,
                widthDiff/2,
                heightDiff/2,
                width,
                height
        );
        return dstBmp;
    }
}
