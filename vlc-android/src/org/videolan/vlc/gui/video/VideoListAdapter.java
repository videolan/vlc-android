/*****************************************************************************
 * VideoListAdapter.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

import org.videolan.libvlc.Media;
import org.videolan.vlc.BitmapCache;
import org.videolan.vlc.MediaGroup;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class VideoListAdapter extends ArrayAdapter<Media>
                                 implements Comparator<Media> {

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private int mSortDirection = 1;
    private int mSortBy = SORT_BY_TITLE;
    private boolean mListMode = false;
    private Context mContext;
    private VideoGridFragment mFragment;

    public VideoListAdapter(Context context, VideoGridFragment fragment) {
        super(context, 0);
        mContext = context;
        mFragment = fragment;
    }

    public final static String TAG = "VLC/MediaLibraryAdapter";

    public synchronized void update(Media item) {
        int position = getPosition(item);
        if (position != -1) {
            remove(item);
            insert(item, position);
        }
    }

    public void setTimes(HashMap<String, Long> times) {
        // update times
        for (int i = 0; i < getCount(); ++i) {
            Media media = getItem(i);
            Long time = times.get(media.getLocation());
            if (time != null)
                media.setTime(time);
        }
    }

    public void sortBy(int sortby) {
        switch (sortby) {
            case SORT_BY_TITLE:
                if (mSortBy == SORT_BY_TITLE)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_TITLE;
                    mSortDirection = 1;
                }
                break;
            case SORT_BY_LENGTH:
                if (mSortBy == SORT_BY_LENGTH)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_LENGTH;
                    mSortDirection *= 1;
                }
                break;
            default:
                mSortBy = SORT_BY_TITLE;
                mSortDirection = 1;
                break;
        }
        sort();
    }

    public void sort() {
        super.sort(this);
    }

    @Override
    public int compare(Media item1, Media item2) {
        int compare = 0;
        switch (mSortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase(Locale.ENGLISH).compareTo(
                        item2.getTitle().toUpperCase(Locale.ENGLISH));
                break;
            case SORT_BY_LENGTH:
                compare = ((Long) item1.getLength()).compareTo(item2.getLength());
                break;
        }
        return mSortDirection * compare;
    }

    /**
     * Display the view of a file browser item.
     */
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;

        if (v == null || (((ViewHolder)v.getTag()).listmode != mListMode)) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if (!mListMode)
                v = inflater.inflate(R.layout.video_grid_item, parent, false);
            else
                v = inflater.inflate(R.layout.video_list_item, parent, false);

            holder = new ViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.thumbnail = (ImageView) v.findViewById(R.id.ml_item_thumbnail);
            holder.title = (TextView) v.findViewById(R.id.ml_item_title);
            holder.subtitle = (TextView) v.findViewById(R.id.ml_item_subtitle);
            holder.progress = (ProgressBar) v.findViewById(R.id.ml_item_progress);
            holder.more = (ImageView) v.findViewById(R.id.item_more);
            holder.listmode = mListMode;
            v.setTag(holder);

            holder.more.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mFragment != null)
                    mFragment.onContextPopupMenu(v, position);
                }
            });

            /* Set the layoutParams based on the values set in the video_grid_item.xml root element */
            v.setLayoutParams(new GridView.LayoutParams(v.getLayoutParams().width, v.getLayoutParams().height));
        } else {
            holder = (ViewHolder) v.getTag();
        }

        Media media = getItem(position);

        /* Thumbnail */
        Bitmap thumbnail = Util.getPictureFromCache(media);
        if (thumbnail == null) {
            // missing thumbnail
            thumbnail = BitmapCache.GetFromResource(v, R.drawable.thumbnail);
        }
        else if (thumbnail.getWidth() == 1 && thumbnail.getHeight() == 1) {
            // dummy thumbnail
            thumbnail = BitmapCache.GetFromResource(v, R.drawable.icon);
        }
        //FIXME Warning: the thumbnails are upscaled in the grid view!
        holder.thumbnail.setImageBitmap(thumbnail);

        /* Color state */
        ColorStateList titleColor = v.getResources().getColorStateList(
                Util.getResourceFromAttribute(mContext, R.attr.list_title));
        holder.title.setTextColor(titleColor);

        if (media instanceof MediaGroup)
            fillGroupView(holder, media);
        else
            fillVideoView(holder, media);

        return v;
    }

    private void fillGroupView(ViewHolder holder, Media media) {
        MediaGroup mediaGroup = (MediaGroup) media;
        int size = mediaGroup.size();
        String text = getContext().getResources().getQuantityString(R.plurals.videos_quantity, size, size);

        holder.subtitle.setText(text);
        holder.title.setText(media.getTitle() + "\u2026"); // ellipsis
        holder.more.setVisibility(View.INVISIBLE);
        holder.progress.setVisibility(View.GONE);
    }

    private void fillVideoView(ViewHolder holder, Media media) {
        /* Time / Duration */
        long lastTime = media.getTime();
        String text;
        if (lastTime > 0) {
            text = String.format("%s / %s",
                    Util.millisToText(lastTime),
                    Util.millisToText(media.getLength()));
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setMax((int) (media.getLength() / 1000));
            holder.progress.setProgress((int) (lastTime / 1000));
        } else {
            text = Util.millisToText(media.getLength());
            holder.progress.setVisibility(View.GONE);
        }

        if (media.getWidth() > 0 && media.getHeight() > 0) {
            text += String.format(" - %dx%d", media.getWidth(), media.getHeight());
        }

        holder.subtitle.setText(text);
        holder.title.setText(media.getTitle());
        holder.more.setVisibility(View.VISIBLE);
    }

    static class ViewHolder {
        boolean listmode;
        View layout;
        ImageView thumbnail;
        TextView title;
        TextView subtitle;
        ImageView more;
        ProgressBar progress;
    }

    public void setListMode(boolean value) {
        mListMode = value;
    }

    public boolean isListMode() {
        return mListMode;
    }
}
