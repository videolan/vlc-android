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
import android.preference.PreferenceManager;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.AudioUtil;

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
        mIsSeenMediaMarkerVisible = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("media_seen", true);

    }

    class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
            mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        void updateCardViewImage(MediaLibraryItem mediaLibraryItem) {
            if (!TextUtils.isEmpty(mediaLibraryItem.getArtworkMrl()))
                AsyncImageLoader.loadPicture(mCardView, mediaLibraryItem);
            else {
                mCardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
                mCardView.setMainImage(new BitmapDrawable(mCardView.getResources(), getDefaultImage(mediaLibraryItem)));
            }
        }

        private Bitmap getDefaultImage(MediaLibraryItem mediaLibraryItem) {
            Bitmap picture;
            final Resources res = mCardView.getResources();
            if (mediaLibraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA && ((MediaWrapper) mediaLibraryItem).getType() == MediaWrapper.TYPE_DIR) {
                MediaWrapper mediaWrapper = (MediaWrapper) mediaLibraryItem;
                if (TextUtils.equals(mediaWrapper.getUri().getScheme(), "file"))
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_folder_big);
                else
                    picture = BitmapFactory.decodeResource(res, R.drawable.ic_menu_network_big);
            } else
                picture = AudioUtil.readCoverBitmap(Uri.decode(mediaLibraryItem.getArtworkMrl()), res.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width));
            if (picture == null) {
                int resId;
                switch (mediaLibraryItem.getItemType()) {
                    case MediaLibraryItem.TYPE_ALBUM:
                        resId = R.drawable.ic_album_big;
                        break;
                    case MediaLibraryItem.TYPE_ARTIST:
                        resId = R.drawable.ic_artist_big;
                        break;
                    case MediaLibraryItem.TYPE_GENRE:
                        resId = R.drawable.ic_genre_big;
                        break;
                    case MediaLibraryItem.TYPE_MEDIA:
                        if (((MediaWrapper)mediaLibraryItem).getType() == MediaWrapper.TYPE_VIDEO)
                            resId = R.drawable.ic_browser_video_big_normal;
                        else
                            resId = R.drawable.ic_song_big;
                        break;
                    default:
                        resId = R.drawable.ic_browser_unknown_big_normal;
                }
                picture = BitmapFactory.decodeResource(res, resId);
            }
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
        ViewHolder holder = ((ViewHolder) viewHolder);
        if (item instanceof MediaWrapper) {
            final MediaWrapper mediaWrapper = (MediaWrapper) item;
            holder.mCardView.setTitleText(mediaWrapper.getTitle());
            holder.mCardView.setContentText(mediaWrapper.getDescription());
            if (mediaWrapper.getType() == MediaWrapper.TYPE_GROUP)
                holder.updateCardViewImage(ContextCompat.getDrawable(mContext, R.drawable.ic_video_collection_big));
            else {
                holder.updateCardViewImage(mediaWrapper);
                if (mIsSeenMediaMarkerVisible
                        && mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO
                        && mediaWrapper.getSeen() > 0L)
                    holder.mCardView.setBadgeImage(ContextCompat.getDrawable(mContext, R.drawable.ic_seen_tv_normal));
            }
            holder.view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    TvUtil.showMediaDetail(v.getContext(), mediaWrapper);
                    return true;
                }
            });
        } else if (item instanceof MediaLibraryItem) {
            MediaLibraryItem mediaLibraryItem = (MediaLibraryItem) item;
            holder.mCardView.setTitleText(mediaLibraryItem.getTitle());
            holder.mCardView.setContentText(mediaLibraryItem.getDescription());
            holder.updateCardViewImage(mediaLibraryItem);
        } else if (item instanceof SimpleCard){
            SimpleCard card = (SimpleCard) item;
            Bitmap image = card.getImage();
            holder.mCardView.setTitleText(card.getName());
            holder.mCardView.setContentText(card.getDescription());
            holder.updateCardViewImage(image != null ? new BitmapDrawable(mRes, image) : ContextCompat.getDrawable(mContext, card.getImageId()));
        } else if (item instanceof String){
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

    static class SimpleCard {
        long id;
        int imageId;
        String name;
        String description;
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
            this.description = "";
            this.imageId = imageId;
        }

        SimpleCard(long id, String name, String description, int imageId){
            this.id = id;
            this.name = name;
            this.description = description;
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

        public String getDescription() {
            return description;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
