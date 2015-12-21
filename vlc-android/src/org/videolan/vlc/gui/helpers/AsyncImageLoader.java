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

package org.videolan.vlc.gui.helpers;

import android.app.Activity;
import android.databinding.OnRebindCallback;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import org.videolan.vlc.BR;
import org.videolan.vlc.VLCApplication;

import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncImageLoader {

    public interface Callbacks {
        Bitmap getImage();
        void updateImage(Bitmap bitmap, View target);
    }

    public final static String TAG = "VLC/AsyncImageLoader";

    /* Maximum one thread that is killed after 2 seconds of inactivity */
    static ThreadPoolExecutor sThreadPool = new ThreadPoolExecutor(0, 1, 2, TimeUnit.SECONDS,
                                                                   new LinkedBlockingQueue<Runnable>());

    public static void LoadImage(final Callbacks cbs, final View target){
        sThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = cbs.getImage();
                cbs.updateImage(bitmap, target);
            }
        });
    }

    public static void LoadAudioCover(final Callable<Bitmap> loader, final ViewDataBinding binding, final Activity activity){
        final Callbacks updater = new Callbacks() {

            @Override
            public Bitmap getImage() {
                try {
                    return loader.call();
                } catch (Exception ignored) {
                    return null;
                }
            }

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
        LoadImage(updater, null);
    }

    public abstract static class CoverFetcher implements AsyncImageLoader.Callbacks {
        final protected ViewDataBinding binding;
        private boolean bindChanged = false;
        final OnRebindCallback<ViewDataBinding> rebindCallbacks = new OnRebindCallback<ViewDataBinding>() {
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

        protected CoverFetcher(ViewDataBinding binding){
            this.binding = binding;
            this.binding.executePendingBindings();
            this.binding.addOnRebindCallback(rebindCallbacks);
        }

        public abstract void updateBindImage(final Bitmap bitmap, View target);

        @Override
        public final void updateImage(final Bitmap bitmap, View target) {
            this.binding.removeOnRebindCallback(rebindCallbacks);
            if (!bindChanged)
                updateBindImage(bitmap, target);
        }
    }
}
