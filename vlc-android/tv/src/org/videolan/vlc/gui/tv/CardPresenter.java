/*****************************************************************************
 * CardPresenter.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioUtil;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class CardPresenter extends Presenter {

    private static final String TAG = "CardPresenter";

    private static Context sContext;
    private static Resources mRes;
    private static int CARD_WIDTH;
    private static int CARD_HEIGHT = 0;
    private static MediaDatabase sMediaDatabase = MediaDatabase.getInstance();
    private static Drawable sDefaultCardImage;

    public CardPresenter(Context context){
        sContext = context;
        mRes = sContext.getResources();
        sDefaultCardImage = mRes.getDrawable(R.drawable.cone);
        CARD_WIDTH = mRes.getDimensionPixelSize(R.dimen.tv_card_width);
        CARD_HEIGHT = mRes.getDimensionPixelSize(R.dimen.tv_card_height);
    }

    static class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
        }

        public ImageCardView getCardView() {
            return mCardView;
        }

        protected void updateCardViewImage(MediaWrapper MediaWrapper) {
            Bitmap picture = null;
            if (MediaWrapper.getType() == MediaWrapper.TYPE_AUDIO)
                picture = AudioUtil.getCover(sContext, MediaWrapper, 320);
            else if (MediaWrapper.getType() == MediaWrapper.TYPE_VIDEO)
                picture = sMediaDatabase.getPicture(sContext, MediaWrapper.getLocation());
            if (picture != null && picture.getByteCount() > 4)
                mCardView.setMainImage(new BitmapDrawable(mRes, picture));
            else
                mCardView.setMainImage(sDefaultCardImage);
        }

        protected void updateCardViewImage(Drawable image) {
            mCardView.setMainImage(image);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {

        ImageCardView cardView = new ImageCardView(sContext);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(mRes.getColor(R.color.lb_details_overview_bg_color));
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
        cardView.getMainImageView().setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder holder = ((ViewHolder) viewHolder);
        if (item instanceof MediaWrapper) {
            MediaWrapper MediaWrapper = (MediaWrapper) item;
            holder.mCardView.setTitleText(MediaWrapper.getTitle());
            holder.mCardView.setContentText(MediaWrapper.getDescription());
            if (MediaWrapper.getType() == MediaWrapper.TYPE_GROUP)
                holder.updateCardViewImage(mRes.getDrawable(
                        R.drawable.ic_video_collection_big));
            else
                holder.updateCardViewImage(MediaWrapper);
        } else if (item instanceof GridFragment.ListItem) {
            GridFragment.ListItem listItem = (GridFragment.ListItem) item;
            MediaWrapper MediaWrapper = listItem.mMediaList.get(0);
            holder.mCardView.setTitleText(listItem.mTitle);
            holder.mCardView.setContentText(listItem.mSubTitle);
            holder.updateCardViewImage(MediaWrapper);
        } else if (item instanceof SimpleCard){
            SimpleCard card = (SimpleCard) item;
            holder.mCardView.setTitleText(card.getName());
            holder.updateCardViewImage(mRes.getDrawable(card.getImageId()));
        }else if (item instanceof String){
            holder.mCardView.setTitleText((String) item);
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
        int id;
        int imageId;
        String name;

        SimpleCard(int id, String name, int imageId){
            this.id = id;
            this.name = name;
            this.imageId = imageId;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getImageId() {
            return imageId;
        }

        public void setImageId(int imageId) {
            this.imageId = imageId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
