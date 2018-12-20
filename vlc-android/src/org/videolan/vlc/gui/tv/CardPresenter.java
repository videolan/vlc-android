/*****************************************************************************
 * CardPresenter.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.vlc.gui.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.ImageLoaderKt;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;

import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class CardPresenter extends Presenter {

    private static final String TAG = "CardPresenter";

    private Activity mContext;
    private Resources mRes;
    private static final int CARD_WIDTH = VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width);
    private static final int CARD_HEIGHT = VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.tv_grid_card_thumb_height);
    private static Drawable sDefaultCardImage;

    private boolean mIsSeenMediaMarkerVisible = true;

    public CardPresenter(Activity context){
        mContext = context;
        mRes = mContext.getResources();
        sDefaultCardImage = ContextCompat.getDrawable(mContext, R.drawable.ic_default_cone);
        mIsSeenMediaMarkerVisible = Settings.INSTANCE.getInstance(context).getBoolean("media_seen", true);

    }

    class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
            mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        void updateCardViewImage(MediaLibraryItem item) {
            final boolean noArt = TextUtils.isEmpty(item.getArtworkMrl());
            if (item instanceof MediaWrapper) {
                final MediaWrapper media = (MediaWrapper) item;
                final boolean group = media.getType() == MediaWrapper.TYPE_GROUP;
                final boolean folder = media.getType() == MediaWrapper.TYPE_DIR;
                final boolean video = media.getType() == MediaWrapper.TYPE_VIDEO;
                if (!folder && (group || (video && !media.isThumbnailGenerated()))) {
                     ImageLoaderKt.loadImage(mCardView, item);
                     return;
                }
            }
            if (noArt) {
                mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
                mCardView.setMainImage(new BitmapDrawable(mCardView.getResources(), getDefaultImage(item)));
            } else ImageLoaderKt.loadImage(mCardView, item);
        }

        private Bitmap getDefaultImage(MediaLibraryItem mediaLibraryItem) {
            Bitmap picture;
            final Resources res = mCardView.getResources();
            if (mediaLibraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA && ((MediaWrapper) mediaLibraryItem).getType() == MediaWrapper.TYPE_DIR) {
                final MediaWrapper mediaWrapper = (MediaWrapper) mediaLibraryItem;
                if (TextUtils.equals(mediaWrapper.getUri().getScheme(), "file"))
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_folder_big);
                else
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_network_big);
            } else picture = AudioUtil.readCoverBitmap(Uri.decode(mediaLibraryItem.getArtworkMrl()), res.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width));
            if (picture == null) picture = BitmapFactory.decodeResource(res, TvUtil.INSTANCE.getIconRes(mediaLibraryItem));
            return picture;
        }

        void updateCardViewImage(Drawable image) {
            mCardView.setMainImage(image);
            mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView cardView = new ImageCardView(mContext);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.lb_details_overview_bg_color));
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, final Object item) {
        final ViewHolder holder = ((ViewHolder) viewHolder);
        if (item instanceof MediaWrapper) {
            final MediaWrapper mw = (MediaWrapper) item;
            holder.mCardView.setTitleText(mw.getTitle());
            holder.mCardView.setContentText(mw.getDescription());
            holder.updateCardViewImage(mw);
            if (mIsSeenMediaMarkerVisible
                    && mw.getType() == MediaWrapper.TYPE_VIDEO
                    && mw.getSeen() > 0L)
                holder.mCardView.setBadgeImage(ContextCompat.getDrawable(mContext, R.drawable.ic_seen_tv_normal));
            holder.view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    TvUtil.INSTANCE.showMediaDetail(v.getContext(), mw);
                    return true;
                }
            });
        } else if (item instanceof MediaLibraryItem) {
            final MediaLibraryItem mediaLibraryItem = (MediaLibraryItem) item;
            holder.mCardView.setTitleText(mediaLibraryItem.getTitle());
            holder.mCardView.setContentText(mediaLibraryItem.getDescription());
            holder.updateCardViewImage(mediaLibraryItem);
        } else if (item instanceof String){
            holder.mCardView.setTitleText((String) item);
            holder.mCardView.setContentText("");
            holder.updateCardViewImage(sDefaultCardImage);
        }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item, List<Object> payloads) {
        if (payloads.isEmpty()) onBindViewHolder(viewHolder, item);
        else {
            final ViewHolder holder = ((ViewHolder) viewHolder);
            final MediaLibraryItem media = (MediaLibraryItem) item;
            for (Object data : payloads) {
                switch ((int) data) {
                    case Constants.UPDATE_DESCRIPTION:
                        holder.mCardView.setContentText(media.getDescription());
                        break;
                    case Constants.UPDATE_THUMB:
                        ImageLoaderKt.loadImage(holder.mCardView, media);
                        break;
                    case Constants.UPDATE_TIME:
                        final MediaWrapper mediaWrapper = (MediaWrapper) item;
                        Tools.setMediaDescription(mediaWrapper);
                        holder.mCardView.setContentText(mediaWrapper.getDescription());
                        if (mediaWrapper.getTime() > 0) break; //update seen check if time is reset to 0
                    case Constants.UPDATE_SEEN:
                        final MediaWrapper mw = (MediaWrapper) item;
                        if (mIsSeenMediaMarkerVisible && mw.getType() == MediaWrapper.TYPE_VIDEO
                                && mw.getSeen() > 0L)
                            holder.mCardView.setBadgeImage(ContextCompat.getDrawable(mContext, R.drawable.ic_seen_tv_normal));
                        break;
                }
            }
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {
        // TODO?
    }
}
