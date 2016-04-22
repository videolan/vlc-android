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

import android.databinding.OnRebindCallback;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v4.util.SimpleArrayMap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader.Callbacks;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpImageLoader implements Callbacks {

    private static SimpleArrayMap<String, SoftReference<Bitmap>> iconsMap = new SimpleArrayMap<>();
    private String mImageLink;
    private ViewDataBinding mBinding;
    private boolean bindChanged = false;
    final OnRebindCallback<ViewDataBinding> rebindCallbacks;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    public HttpImageLoader(String imageLink) {
        mImageLink = imageLink;
        rebindCallbacks = null;
    }

    public HttpImageLoader(String imageLink, ViewDataBinding binding) {
        mImageLink = imageLink;
        mBinding = binding;
        mBinding = binding;
        mBinding.executePendingBindings();
        rebindCallbacks = new OnRebindCallback<ViewDataBinding>() {
            @Override
            public boolean onPreBind(ViewDataBinding binding) {
                bindChanged = true;
                return super.onPreBind(binding);
            }

            @Override
            public void onCanceled(ViewDataBinding binding) {
                super.onCanceled(binding);
            }

            @Override
            public void onBound(ViewDataBinding binding) {
                super.onBound(binding);
            }
        };
        mBinding.addOnRebindCallback(rebindCallbacks);
    }

    @Override
    public Bitmap getImage() {
        return downloadBitmap(mImageLink);
    }

    @Nullable
    public static Bitmap downloadBitmap(String imageUrl) {
        if (iconsMap.containsKey(imageUrl)) {
            Bitmap bd = iconsMap.get(imageUrl).get();
            if (bd != null) {
                return bd;
            } else
                iconsMap.remove(imageUrl);
        }
        HttpURLConnection urlConnection = null;
        Bitmap icon = null;
        try {
            URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            icon = BitmapFactory.decodeStream(in);
            iconsMap.put(imageUrl, new SoftReference<>(icon));
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
    public void updateImage(final Bitmap bitmap, final View target) {
        if (bitmap == null || bitmap.getWidth() == 1 || bitmap.getHeight() == 1)
            return;
        if (mBinding != null) {
            mBinding.removeOnRebindCallback(rebindCallbacks);
            if (bindChanged)
                return;
            mBinding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
            mBinding.setVariable(BR.image, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
            mBinding.setVariable(BR.protocol, null);
        } else {
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (target instanceof ImageCardView)
                        ((ImageCardView) target).setMainImage(new BitmapDrawable(target.getResources(), bitmap));
                    else if (target instanceof ImageView)
                        ((ImageView) target).setImageBitmap(bitmap);
                    else if (target instanceof TextView)
                        target.setBackgroundDrawable(new BitmapDrawable(target.getResources(), bitmap));
                }
            });
        }
    }
}
