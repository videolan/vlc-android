/*
 * *************************************************************************
 *  AsyncImageLoader.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui;

import android.app.Activity;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;

import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncImageLoader {

    public interface ImageUpdater {
        void updateImage(Bitmap bitmap, View target);
    }

    public final static String TAG = "VLC/AsyncImageLoader";

    /* Maximum one thread that is killed after 2 seconds of inactivity */
    static ThreadPoolExecutor sThreadPool = new ThreadPoolExecutor(0, 1, 2, TimeUnit.SECONDS,
                                                                   new LinkedBlockingQueue<Runnable>());

    public static void LoadImage(final Callable<Bitmap> loader, final ImageUpdater updater, final View target){
        sThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                handleImage(updater, loader, target);
            }
        });
    }

    public static void LoadAudioCover(Callable<Bitmap> loader, final ViewDataBinding binding, final Activity activity){
        ImageUpdater updater = new ImageUpdater() {
            @Override
            public void updateImage(final Bitmap bitmap, View target) {
                if (bitmap != null && activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.setVariable(BR.cover, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                            binding.executePendingBindings();
                        }
                    });
                }

            }
        };
        LoadImage(loader, updater, null);
    }

    public static void LoadVideoCover(Callable<Bitmap> loader, final ViewDataBinding binding, final Activity activity){
        ImageUpdater updater = new ImageUpdater() {
            @Override
            public void updateImage(final Bitmap bitmap, View target) {
                if (bitmap != null && activity != null &&
                        (bitmap.getWidth() != 1 && bitmap.getHeight() != 1)) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
                            binding.setVariable(BR.cover, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                            binding.executePendingBindings();
                        }
                    });
                }

            }
        };
        LoadImage(loader, updater, null);
    }

    private static void handleImage(final ImageUpdater updater, Callable<Bitmap> loader, View target){
        try {
            final Bitmap bitmap = loader.call();
            updater.updateImage(bitmap, target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
