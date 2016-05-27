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
import android.os.Handler;
import android.os.Looper;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.gui.tv.browser.MusicFragment;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.HttpImageLoader;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class CardPresenter extends Presenter {

    private static final String TAG = "CardPresenter";

    private Activity mContext;
    private Resources mRes;
    private static int CARD_WIDTH;
    private static int CARD_HEIGHT = 0;
    private static Drawable sDefaultCardImage;
    private static Handler sHandler = new Handler(Looper.getMainLooper());

    public CardPresenter(Activity context){
        mContext = context;
        mRes = mContext.getResources();
        sDefaultCardImage = mRes.getDrawable(R.drawable.ic_no_thumbnail_big);
        CARD_WIDTH = mRes.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width);
        CARD_HEIGHT = mRes.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_height);
    }

    class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
        }

        protected void updateCardViewImage(MediaWrapper mediaWrapper) {
            mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (!TextUtils.isEmpty(mediaWrapper.getArtworkURL()) && mediaWrapper.getArtworkURL().startsWith("http")) {
                AsyncImageLoader.LoadImage(new HttpImageLoader(mediaWrapper.getArtworkURL()), mCardView);
            } else {
                AsyncImageLoader.LoadImage(new CoverFetcher(mediaWrapper), mCardView);
            }
        }

        protected void updateCardViewImage(Drawable image) {
            mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
            mCardView.setMainImage(image);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {

        ImageCardView cardView = new ImageCardView(mContext);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(mRes.getColor(R.color.lb_details_overview_bg_color));
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, final Object item) {
        ViewHolder holder = ((ViewHolder) viewHolder);
        if (item instanceof MediaWrapper) {
            final MediaWrapper mediaWrapper = (MediaWrapper) item;
            holder.mCardView.setTitleText(mediaWrapper.getTitle());
            holder.mCardView.setContentText(mediaWrapper.getDescription());
            if (mediaWrapper.getType() == mediaWrapper.TYPE_GROUP)
                holder.updateCardViewImage(mRes.getDrawable(
                        R.drawable.ic_video_collection_big));
            else
                holder.updateCardViewImage(mediaWrapper);
            holder.view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    TvUtil.showMediaDetail(v.getContext(), mediaWrapper);
                    return true;
                }
            });
        } else if (item instanceof MusicFragment.ListItem) {
            MusicFragment.ListItem listItem = (MusicFragment.ListItem) item;
            MediaWrapper MediaWrapper = listItem.mediaList.get(0);
            holder.mCardView.setTitleText(listItem.mTitle);
            holder.mCardView.setContentText(listItem.mSubTitle);
            holder.updateCardViewImage(MediaWrapper);
        } else if (item instanceof SimpleCard){
            SimpleCard card = (SimpleCard) item;
            Bitmap image = card.getImage();
            holder.mCardView.setTitleText(card.getName());
            holder.mCardView.setContentText("");
            holder.updateCardViewImage(image != null ? new BitmapDrawable(image) : mRes.getDrawable(card.getImageId()));
        }else if (item instanceof String){
            holder.mCardView.setTitleText((String) item);
            holder.mCardView.setContentText("");
            holder.updateCardViewImage(sDefaultCardImage);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {
        // TODO?
    }

    public static class SimpleCard {
        long id;
        int imageId;
        String name;
        Bitmap image;

        Uri uri;

        SimpleCard(long id, String name, Bitmap image){
            this.id = id;
            this.name = name;
            this.image = image;
        }

        SimpleCard(long id, String name, int imageId){
            this.id = id;
            this.name = name;
            this.imageId = imageId;
        }

        SimpleCard(long id, String name, int imageId, Uri uri){
            this(id, name, imageId);
            this.uri = uri;
        }

        public Uri getUri() {
            return uri;
        }

        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getImageId() {
            return imageId;
        }

        public void setImageId(int imageId) {
            this.image = null;
            this.imageId = imageId;
        }

        public Bitmap getImage() {
            return image;
        }

        public void setImage(Bitmap image) {
            this.image = image;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class CoverFetcher implements AsyncImageLoader.Callbacks{
        MediaWrapper mediaWrapper;
        private static Resources res;

        CoverFetcher(MediaWrapper mediaWrapper){
            this.mediaWrapper = mediaWrapper;
            res = VLCApplication.getAppResources();
        }

        @Override
        public Bitmap getImage() {
            Bitmap picture;
            if (mediaWrapper.getType() == mediaWrapper.TYPE_AUDIO) {
                picture = AudioUtil.getCover(VLCApplication.getAppContext(), mediaWrapper, 320);
                if (picture == null)
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_browser_audio_big_normal);
            } else if (mediaWrapper.getType() == mediaWrapper.TYPE_VIDEO) {
                picture = BitmapUtil.getPicture(mediaWrapper);
                if (picture == null)
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_browser_video_big_normal);
            } else if (mediaWrapper.getType() == mediaWrapper.TYPE_DIR) {
                if (TextUtils.equals(mediaWrapper.getUri().getScheme(),"file"))
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_folder_big);
                else
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_network_big);
            }
            else
                picture = BitmapFactory.decodeResource(res, R.drawable.ic_browser_unknown_big_normal);
            return picture;
        }

        @Override
        public void updateImage(final Bitmap picture, final View target) {
            sHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageCardView cardView = (ImageCardView) target;
                            if (picture != null && picture.getByteCount() > 4)
                                cardView.setMainImage(new BitmapDrawable(res, picture));
                            else
                                cardView.setMainImage(sDefaultCardImage);
                        }
                    }
            );
        }
    }
}
