/*
 * ************************************************************************
 *  HttpImageLoader.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.util;

import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.util.SimpleArrayMap;
import android.view.View;

import org.videolan.vlc.gui.helpers.AsyncImageLoader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpImageLoader extends AsyncImageLoader.CoverFetcher {

    private static SimpleArrayMap<String, SoftReference<Bitmap>> iconsMap = new SimpleArrayMap<>();
    private String mImageLink;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    public HttpImageLoader(String imageLink, ViewDataBinding binding) {
        init(binding);
        mImageLink = imageLink;
    }

    @Override
    public Bitmap getImage() {
        return downloadBitmap(mImageLink);
    }

    @Nullable
    public static Bitmap getBitmapFromIconCache(String imageUrl) {
        synchronized (iconsMap) {
            if (iconsMap.containsKey(imageUrl)) {
                final Bitmap bd = iconsMap.get(imageUrl).get();
                if (bd != null) {
                    return bd;
                } else
                    iconsMap.remove(imageUrl);
            }
        }
        return null;
    }

    @Nullable
    public static Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection urlConnection = null;
        InputStream in = null;
        Bitmap icon = getBitmapFromIconCache(imageUrl);
        if (icon != null)
            return icon;
        try {
            final URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream());
            icon = BitmapFactory.decodeStream(in);
            synchronized (iconsMap) {
                iconsMap.put(imageUrl, new SoftReference<>(icon));
            }
        } catch (IOException|IllegalArgumentException ignored) {
        } finally {
            Util.close(in);
            if (urlConnection != null)
                urlConnection.disconnect();
        }
        return icon;
    }

    @Override
    public void updateImage(final Bitmap bitmap, final View target) {
        AsyncImageLoader.updateTargetImage(bitmap, target, binding);
    }
}
