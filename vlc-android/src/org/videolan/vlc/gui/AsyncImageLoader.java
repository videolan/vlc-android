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
import android.graphics.Bitmap;
import android.widget.ImageView;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncImageLoader {
    public final static String TAG = "VLC/AsyncImageLoader";

    static ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void LoadImage(Callable<Bitmap> loader, final ImageView view, Activity activity){

        Future<Bitmap> future = executor.submit(loader);
        handleImage(view, activity, future);
    }

    private static void handleImage(final ImageView view, final Activity activity, final Future<Bitmap> future) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = future.get();

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            view.post(new Runnable() {
                                @Override
                                public void run() {
                                    view.setImageBitmap(bitmap);
                                }
                            });
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
