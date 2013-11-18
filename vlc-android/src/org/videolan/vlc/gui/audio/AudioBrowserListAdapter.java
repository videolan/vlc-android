/*****************************************************************************
 * AudioSongsListAdapter.java
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

package org.videolan.vlc.gui.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.videolan.vlc.BitmapCache;
import org.videolan.vlc.Media;
import org.videolan.vlc.R;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AudioBrowserListAdapter implements ListAdapter {

    // Key: the item title, value: ListItem of only media item (no separator).
    private Map<String, ListItem> mMediaItemMap;
    // A list of all the list items: media items and separators.
    private ArrayList<ListItem> mItems;

    private Context mContext;

    // The types of the item views: media and separator.
    private static final int VIEW_MEDIA = 0;
    private static final int VIEW_SEPARATOR = 1;

    // The types of the media views.
    public static final int ITEM_SIMPLE = 0;
    public static final int ITEM_NORMAL = 1;
    private int mItemType;

    // An item of the list: a media or a separator.
    class ListItem {
        public String mTitle;
        public String mSubTitle;
        public ArrayList<Media> mMediaList;
        public boolean mIsSeparator;

        public ListItem(String title, String subTitle,
                Media media, boolean isSeparator) {
            if (!isSeparator) {
                mMediaList = new ArrayList<Media>();
                mMediaList.add(media);
            }
            mTitle = title;
            mSubTitle = subTitle;
            mIsSeparator = isSeparator;
        }
    }

    public AudioBrowserListAdapter(Context context, int itemType) {
        mMediaItemMap = new HashMap<String, ListItem>();
        mItems = new ArrayList<ListItem>();
        mContext = context;
        if (itemType != ITEM_SIMPLE && itemType != ITEM_NORMAL)
            throw new IllegalArgumentException();
        mItemType = itemType;
    }

    public void add(String title, String subTitle, Media media) {
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, subTitle, media, false);
            mMediaItemMap.put(title, item);
            mItems.add(item);
        }
    }

    public void addSeparator(String title) {
        ListItem item = new ListItem(title, null, null, true);
        mMediaItemMap.put(title, item);
        mItems.add(item);
    }

    public void clear() {
        mMediaItemMap.clear();
        mItems.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getItemViewType(position) == VIEW_MEDIA)
            return getViewMedia(position, convertView, parent);
        else // == VIEW_SEPARATOR
            return getViewSeparator(position, convertView, parent);
    }

    public View getViewMedia(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder holder;

        /* convertView may be a recycled view but we must recreate it
         * if it does not correspond to a media view. */
        boolean b_createView = true;
        if (v != null) {
            holder = (ViewHolder) v.getTag();
            if (holder.viewType == VIEW_MEDIA)
                b_createView = false;
        }

        if (b_createView) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            int res = mItemType == ITEM_SIMPLE ? R.layout.audio_browser_item_simple : R.layout.audio_browser_item;
            v = inflater.inflate(res, parent, false);
            holder = new ViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            if (mItemType == ITEM_NORMAL) {
                holder.cover = (ImageView) v.findViewById(R.id.cover);
                holder.artist = (TextView) v.findViewById(R.id.artist);
            }
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        ListItem item = getItem(position);
        holder.title.setText(item.mTitle);

        if (mItemType == ITEM_NORMAL) {
            /*Bitmap cover = AudioUtil.getCover(v.getContext(), media, 64);
            if (cover == null)*/
            Bitmap cover = BitmapCache.GetFromResource(v, R.drawable.icon);
            holder.cover.setImageBitmap(cover);
            holder.artist.setText(item.mSubTitle);
        }

        return v;
    }

    public View getViewSeparator(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder holder;

        /* convertView may be a recycled view but we must recreate it
         * if it does not correspond to a separator view. */
        boolean b_createView = true;
        if (v != null) {
            holder = (ViewHolder) v.getTag();
            if (holder.viewType == VIEW_SEPARATOR)
                b_createView = false;
        }

        if (b_createView) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_separator, parent, false);
            holder = new ViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        ListItem item = getItem(position);
        holder.title.setText(item.mTitle);

        return v;
    }

    class ViewHolder {
        View layout;
        ImageView cover;
        TextView title;
        TextView artist;
        int viewType;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public ListItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        int viewType = 0;
        if (mItems.get(position).mIsSeparator)
            viewType = 1;
        return viewType;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) { }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) { }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }
}
