package org.videolan.vlc.gui.tv;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.ViewGroup;

public class CardPresenter extends Presenter {

	private static final String TAG = "CardPresenter";

    private static Context sContext;
    private static int CARD_WIDTH = 0;
    private static int CARD_HEIGHT = 0;
    private static Resources sResources;
    private static MediaDatabase sMediaDatabase = MediaDatabase.getInstance();
    private static Drawable sDefaultCardImage;

    static class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
        }

        public ImageCardView getCardView() {
            return mCardView;
        }

        protected void updateCardViewImage(String mediaLocation) {
			Bitmap picture = sMediaDatabase.getPicture(sContext, mediaLocation);
			if (picture.getByteCount() > 4)
				mCardView.setMainImage(new BitmapDrawable(sResources, picture));
			else
				updateCardViewImage(sDefaultCardImage);
        }

        protected void updateCardViewImage(Drawable image) {
        	mCardView.setMainImage(image);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sContext = parent.getContext();
        sResources = sContext.getResources();
        sDefaultCardImage = sContext.getResources().getDrawable(R.drawable.cone);
        if (CARD_WIDTH == 0) {
			CARD_WIDTH = sResources.getDimensionPixelSize(
					R.dimen.tv_card_width);
			CARD_HEIGHT = sResources.getDimensionPixelSize(
					R.dimen.tv_card_height);
		}

        ImageCardView cardView = new ImageCardView(sContext);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(sContext.getResources().getColor(R.color.lb_details_overview_bg_color));
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ((ViewHolder) viewHolder).mCardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
    	if (item instanceof Media) {
	    	Media media = (Media) item;
	        ((ViewHolder) viewHolder).mCardView.setTitleText(media.getTitle());
	        ((ViewHolder) viewHolder).mCardView.setContentText(media.getDescription());
	        if (media.isPictureParsed())
				((ViewHolder) viewHolder).updateCardViewImage(media.getLocation());
			else
				((ViewHolder) viewHolder).updateCardViewImage(sDefaultCardImage);
    	} else if (item instanceof GridFragment.ListItem) {
	    	GridFragment.ListItem listItem = (GridFragment.ListItem) item;
	    	Media media = listItem.mMediaList.get(0);
	        ((ViewHolder) viewHolder).mCardView.setTitleText(listItem.mTitle);
	        ((ViewHolder) viewHolder).mCardView.setContentText(listItem.mSubTitle);
	        if (media.isPictureParsed())
				((ViewHolder) viewHolder).updateCardViewImage(media.getLocation());
			else
				((ViewHolder) viewHolder).updateCardViewImage(sDefaultCardImage);
    	} else if (item instanceof String){
    		((ViewHolder) viewHolder).mCardView.setTitleText((String) item);
    		((ViewHolder) viewHolder).updateCardViewImage(sDefaultCardImage);
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
