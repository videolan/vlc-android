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
import android.databinding.DataBindingUtil;
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

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.util.HttpImageLoader;
import org.videolan.vlc.util.Strings;

public class AsyncImageLoader {

    public interface Callbacks {
        Bitmap getImage();
        void updateImage(Bitmap bitmap, View target);
    }

    public final static String TAG = "VLC/AsyncImageLoader";
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    public static final Bitmap DEFAULT_COVER_VIDEO = BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.ic_no_thumbnail_1610);
    public static final BitmapDrawable DEFAULT_COVER_VIDEO_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), DEFAULT_COVER_VIDEO);
    public static final Bitmap DEFAULT_COVER_AUDIO = BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.icon);
    public static final BitmapDrawable DEFAULT_COVER_AUDIO_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), DEFAULT_COVER_AUDIO);

    /*
     * Custom bindings to trigger image (down)loading
     */

    @BindingAdapter({"imageUri"})
    public static void downloadIcon(final View v, final Uri imageUri) {
        AsyncImageLoader.LoadImage(new CoverFetcher(null) {
            @Override
            public Bitmap getImage() {
                return HttpImageLoader.downloadBitmap(imageUri.toString());
            }

            @Override
            public void updateImage(Bitmap bitmap, View target) {
                updateTargetImage(bitmap, v, binding, 0);
            }
        }, v);
    }

    @BindingAdapter({"mediaWithArt"})
    public static void downloadIcon(View v, MediaLibraryItem item) {
        if (item == null || item.getItemType() != MediaLibraryItem.TYPE_MEDIA ||
                TextUtils.isEmpty(item.getArtworkMrl()) || !item.getArtworkMrl().startsWith("http"))
            return;
        MediaWrapper mw = (MediaWrapper) item;
        AsyncImageLoader.LoadImage(new MLItemCoverFetcher(v, mw), v);
    }

    @BindingAdapter({"media"})
    public static void loadPicture(View v, MediaLibraryItem item) {
        if (item == null)
            return;
        if (item instanceof MediaWrapper) {
            if (item instanceof MediaGroup)
                item = ((MediaGroup) item).getFirstMedia();
            int type = ((MediaWrapper) item).getType();
            final Bitmap bitmap = type == MediaWrapper.TYPE_VIDEO ?
                    BitmapUtil.getPictureFromCache((MediaWrapper) item) : null;
            if (bitmap != null) {
                updateTargetImage(bitmap, v, DataBindingUtil.findBinding(v), type);
                return;
            }
        }
        AsyncImageLoader.LoadImage(new MLItemCoverFetcher(v, item), v);
    }

    public static void LoadImage(final Callbacks cbs, final View target){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = cbs.getImage();
                cbs.updateImage(bitmap, target);
            }
        });
    }

    private static class MLItemCoverFetcher extends AsyncImageLoader.CoverFetcher {
        MediaLibraryItem item;
        int width;

        MLItemCoverFetcher(View v, MediaLibraryItem item) {
            super(DataBindingUtil.findBinding(v));
            this.item = item;
            width = v.getWidth();
        }

        @Override
        public Bitmap getImage() {
            if (bindChanged)
                return null;
            String artworkUrl = item.getArtworkMrl();
            if (!TextUtils.isEmpty(artworkUrl) && artworkUrl.startsWith("http"))
                return HttpImageLoader.downloadBitmap(artworkUrl);
            return AudioUtil.readCoverBitmap(Strings.removeFileProtocole(Uri.decode(item.getArtworkMrl())), width);
        }

        @Override
        public void updateImage(Bitmap bitmap, View target) {
            if (!bindChanged)
                updateTargetImage(bitmap, target, binding, item.getItemType() == MediaLibraryItem.TYPE_MEDIA ? ((MediaWrapper) item).getType() : MediaWrapper.TYPE_AUDIO);
        }
    }

    private static void updateTargetImage(final Bitmap bitmap, final View target, final ViewDataBinding vdb, final int type) {
        if (vdb != null) {
            if (bitmap != null && bitmap.getWidth() != 1 && bitmap.getHeight() != 1) {
                vdb.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
                vdb.setVariable(BR.cover, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                vdb.setVariable(BR.protocol, null);
            } else
                vdb.setVariable(BR.cover, type == MediaWrapper.TYPE_VIDEO ? DEFAULT_COVER_VIDEO_DRAWABLE : AudioUtil.DEFAULT_COVER);
        } else {
            final boolean isBitmapValid = bitmap != null && bitmap.getWidth() != 1 && bitmap.getHeight() != 1;
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (target instanceof ImageView) {
                        ImageView iv = (ImageView) target;
                        iv.setVisibility(View.VISIBLE);
                        if (isBitmapValid) {
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            iv.setImageBitmap(bitmap);
                        } else
                            iv.setImageResource(type == MediaWrapper.TYPE_VIDEO ? R.drawable.ic_no_thumbnail_1610 : R.drawable.icon);
                    } else if (target instanceof TextView) {
                        if (isBitmapValid) {
                            target.setBackgroundDrawable(new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
                            ((TextView) target).setText(null);
                        }
                    }
                }
            });
        }
    }

    abstract static class CoverFetcher implements AsyncImageLoader.Callbacks {
        protected ViewDataBinding binding = null;
        boolean bindChanged = false;
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

        CoverFetcher(ViewDataBinding binding){
            if (binding != null) {
                this.binding = binding;
                this.binding.executePendingBindings();
                this.binding.addOnRebindCallback(rebindCallbacks);
            }
        }
    }
}
