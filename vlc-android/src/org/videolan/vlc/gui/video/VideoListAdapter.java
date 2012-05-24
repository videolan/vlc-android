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

import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class VideoListAdapter extends ArrayAdapter<Media>
                                 implements Comparator<Media> {

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private int mSortDirection = 1;
    private int mSortBy = SORT_BY_TITLE;
    private long mLastTime;
    private String mLastMRL;

    public VideoListAdapter(Context context) {
        super(context, 0);
    }

    public final static String TAG = "VLC/MediaLibraryAdapter";

    public synchronized void update(Media item) {
        int position = getPosition(item);
        if (position != -1) {
            remove(item);
            insert(item, position);
        }
    }

    public void setLastMedia(long lastTime, String lastMRL) {
        mLastTime = lastTime;
        mLastMRL = lastTime > 0 ? lastMRL : null;
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

    public int compare(Media item1, Media item2) {
        int compare = 0;
        switch (mSortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase().compareTo(
                        item2.getTitle().toUpperCase());
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
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.video_list_item, parent, false);
            holder = new ViewHolder();
            holder.layout = (View) v.findViewById(R.id.layout_item);
            holder.thumbnail = (ImageView) v.findViewById(R.id.ml_item_thumbnail);
            holder.title = (TextView) v.findViewById(R.id.ml_item_title);
            holder.subtitle = (TextView) v.findViewById(R.id.ml_item_subtitle);
            holder.more = (ImageView) v.findViewById(R.id.ml_item_more);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media media = getItem(position);
        Util.setItemBackground(holder.layout, position);
        holder.title.setText(media.getTitle());

        Bitmap thumbnail;
        if (media.getPicture() != null) {
            thumbnail = media.getPicture();
            holder.thumbnail.setImageBitmap(thumbnail);
        } else {
            // set default thumbnail
            thumbnail = BitmapFactory.decodeResource(v.getResources(), R.drawable.thumbnail);
            holder.thumbnail.setImageBitmap(thumbnail);
        }

        if (media.getLocation().equals(mLastMRL))
        {
            holder.title.setTextColor(0xFFF48B00 /* ORANGE */);
            holder.subtitle.setText(String.format("%s / %s - %dx%d",
                    Util.millisToString(mLastTime),
                    Util.millisToString(media.getLength()),
                    media.getWidth(), media.getHeight()));
        }
        else
        {
            holder.title.setTextColor(Color.WHITE);
            holder.subtitle.setText(String.format("%s - %dx%d",
                    Util.millisToString(media.getLength()),
                    media.getWidth(), media.getHeight()));
        }
        holder.more.setTag(media);
        holder.more.setOnClickListener(moreClickListener);

        return v;
    }

    private OnClickListener moreClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Media item = (Media) v.getTag();
            Intent intent = new Intent(getContext(), MediaInfoActivity.class);
            intent.putExtra("itemLocation", item.getLocation());
            getContext().startActivity(intent);
        }
    };

    static class ViewHolder {
        View layout;
        ImageView thumbnail;
        TextView title;
        TextView subtitle;
        ImageView more;
    }
}
