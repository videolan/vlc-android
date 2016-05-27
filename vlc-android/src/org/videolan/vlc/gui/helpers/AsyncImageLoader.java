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

import android.databinding.BindingAdapter;
import android.databinding.OnRebindCallback;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserListAdapter;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.HttpImageLoader;

public class AsyncImageLoader {

    public interface Callbacks {
        Bitmap getImage();
        void updateImage(Bitmap bitmap, View target);
    }

    public final static String TAG = "VLC/AsyncImageLoader";
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    public static final BitmapDrawable DEFAULT_COVER_VIDEO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.icon));

    public static void LoadImage(final Callbacks cbs, final View target){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = cbs.getImage();
                cbs.updateImage(bitmap, target);
            }
        });
    }

    public abstract static class CoverFetcher implements AsyncImageLoader.Callbacks {
        protected ViewDataBinding binding = null;
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
            if (binding != null) {
                this.binding = binding;
                this.binding.executePendingBindings();
                this.binding.addOnRebindCallback(rebindCallbacks);
            }
        }

        public void updateBindImage(final Bitmap bitmap) {}
        public void updateImageView(final Bitmap bitmap, View target) {}

        @Override
        public void updateImage(final Bitmap bitmap, final View target) {
            if (binding != null) {
                this.binding.removeOnRebindCallback(rebindCallbacks);
                if (!bindChanged)
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateBindImage(bitmap);
                        }
                    });
            } else  {
                sHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateImageView(bitmap, target);
                    }
                });
            }
        }
    }

    /*
     * Custom bindings to trigger image (dwon)loading
     */

    @BindingAdapter({"bind:imageUri", "bind:binding"})
    public static void downloadIcon(final View v, final Uri imageUri, final ViewDataBinding vdb) {
        AsyncImageLoader.LoadImage(new Callbacks() {
            @Override
            public Bitmap getImage() {
                return HttpImageLoader.downloadBitmap(imageUri.toString());
            }

            @Override
            public void updateImage(Bitmap bitmap, View target) {
                if (v instanceof ImageView)
                    setCover((ImageView) v, 0, bitmap, vdb);
            }
        }, v);
    }


    @BindingAdapter({"bind:mediaWithArt"})
    public static void downloadIcon(View v, MediaWrapper mw) {
        if (mw == null)
            return;
        ViewDataBinding vdb = (ViewDataBinding) v.getTag();
        if (TextUtils.isEmpty(mw.getArtworkURL()) || !mw.getArtworkURL().startsWith("http"))
            return;
        if (vdb == null && v.getTag() instanceof ViewDataBinding)
            vdb = (ViewDataBinding) v.getTag();
        AsyncImageLoader.LoadImage(new MediaCoverFetcher(vdb, mw), v);

    }

    @BindingAdapter({"bind:media"})
    public static void loadPicture(ImageView v, MediaWrapper mw) {
        ViewDataBinding vdb = null;
        if (v.getTag() instanceof ViewDataBinding)
            vdb = (ViewDataBinding) v.getTag();
        loadPicture(v, mw, vdb);
    }

    @BindingAdapter({"bind:item"})
    public static void loadPicture(final ImageView v, final AudioBrowserListAdapter.ListItem item) {
        final Object tag = v.getTag();
        if (tag == null || !(tag instanceof ViewDataBinding))
            return;
        Bitmap bitmap = AudioUtil.getCoverFromMemCache(VLCApplication.getAppContext(), item.mMediaList, 64);
        if (bitmap != null) {
            ((ViewDataBinding) tag).setVariable(BR.cover, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
            return;
        }
        AsyncImageLoader.LoadImage(new Callbacks() {
            @Override
            public Bitmap getImage() {
                return AudioUtil.getCover(VLCApplication.getAppContext(), item.mMediaList, 64);
            }

            @Override
            public void updateImage(final Bitmap bitmap, View target) {
                sHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setCover(v, MediaWrapper.TYPE_AUDIO, bitmap, (ViewDataBinding) tag);
                    }
                });
            }
        }, v);
    }

    @BindingAdapter({"bind:media", "bind:binding"})
    public static void loadPicture(ImageView v, MediaWrapper mw, ViewDataBinding vdb) {
        if (mw instanceof MediaGroup)
            mw = ((MediaGroup) mw).getFirstMedia();
        final Bitmap bitmap = mw.getType() == MediaWrapper.TYPE_VIDEO ?
                BitmapUtil.getPictureFromCache(mw) :
                AudioUtil.getCoverFromMemCache(v.getContext(), mw, 64);
        if (bitmap != null)
            setCover(v, mw.getType(), bitmap, vdb);
        else
            AsyncImageLoader.LoadImage(new MediaCoverFetcher(vdb, mw), v);

    }

    private static void setCover(ImageView iv, int type, Bitmap bitmap, ViewDataBinding vdb) {
        if (vdb != null) {
            if (bitmap != null && bitmap.getWidth() != 1 && bitmap.getHeight() != 1) {
                vdb.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
                vdb.setVariable(BR.cover, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
            } else
                vdb.setVariable(BR.cover, type == MediaWrapper.TYPE_VIDEO ? DEFAULT_COVER_VIDEO : AudioUtil.DEFAULT_COVER);
        } else {
            iv.setVisibility(View.VISIBLE);
            if (bitmap != null && bitmap.getWidth() != 1 && bitmap.getHeight() != 1) {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageBitmap(bitmap);
            } else {
                iv.setImageResource(type == MediaWrapper.TYPE_VIDEO ? R.drawable.ic_cone_o : R.drawable.icon);
            }
        }
    }

    private static class MediaCoverFetcher extends AsyncImageLoader.CoverFetcher {
        final MediaWrapper media;

        MediaCoverFetcher(ViewDataBinding binding, MediaWrapper media) {
            super(binding);
            this.media = media;
        }

        @Override
        public Bitmap getImage() {
            if (!TextUtils.isEmpty(media.getArtworkURL()) && media.getArtworkURL().startsWith("http"))
                return HttpImageLoader.downloadBitmap(media.getArtworkURL());
            return media.getType() == MediaWrapper.TYPE_VIDEO ? BitmapUtil.fetchPicture(media) :
                    AudioUtil.getCover(VLCApplication.getAppContext(), media, 64);
        }

        @Override
        public void updateImage(final Bitmap bitmap, final View target) {
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (target instanceof ImageView)
                        setCover((ImageView) target, media.getType(), bitmap, binding);
                    else if (target instanceof TextView) {
                        if (bitmap != null && (bitmap.getWidth() != 1 && bitmap.getHeight() != 1)) {
                            if (binding != null) {
                                binding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
                                binding.setVariable(BR.image, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                                binding.setVariable(BR.protocol, null);
                            } else {
                                target.setBackgroundDrawable(new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                                ((TextView) target).setText(null);
                            }
                        }
                    }
                }
            });
        }
    }
}
