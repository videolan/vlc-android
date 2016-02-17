/*
 * ************************************************************************
 *  HttpImageFetcher.java
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
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.util.SimpleArrayMap;
import android.view.View;
import android.widget.ImageView;

import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpImageFetcher extends AsyncImageLoader.CoverFetcher {
    static SimpleArrayMap<String, SoftReference<Bitmap>> iconsMap = new SimpleArrayMap<>();
    final String imageLink;

    public HttpImageFetcher(ViewDataBinding binding, String imageLink) {
        super(binding);
        this.imageLink = imageLink;
    }

    @Override
    public Bitmap getImage() {
        if (iconsMap.containsKey(imageLink)) {
            Bitmap bd = iconsMap.get(imageLink).get();
            if (bd != null) {
                return bd;
            } else
                iconsMap.remove(imageLink);
        }
        HttpURLConnection urlConnection = null;
        Bitmap icon = null;
        try {
            URL url = new URL(imageLink);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            icon = BitmapFactory.decodeStream(in);
            iconsMap.put(imageLink, new SoftReference<>(icon));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
        return icon;
    }

    @Override
    public void updateBindImage(Bitmap bitmap, View target) {
        if (bitmap != null && (bitmap.getWidth() != 1 && bitmap.getHeight() != 1)) {
            binding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
            binding.setVariable(BR.image, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
        }
    }
}
