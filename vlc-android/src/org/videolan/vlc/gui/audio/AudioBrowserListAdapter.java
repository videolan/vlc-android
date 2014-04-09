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
import java.util.List;
import java.util.Map;

import org.videolan.vlc.BitmapCache;
import org.videolan.libvlc.Media;
import org.videolan.vlc.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class AudioBrowserListAdapter extends BaseAdapter {

    // Key: the item title, value: ListItem of only media item (no separator).
    private Map<String, ListItem> mMediaItemMap;
    // A list of all the list items: media items and separators.
    private ArrayList<ListItem> mItems;

    private Context mContext;

    // The types of the item views: media and separator.
    private static final int VIEW_MEDIA = 0;
    private static final int VIEW_SEPARATOR = 1;

    // The types of the media views.
    public static final int ITEM_WITHOUT_COVER = 0;
    public static final int ITEM_WITH_COVER = 1;
    private int mItemType;

    private ContextPopupMenuListener mContextPopupMenuListener;

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
        if (itemType != ITEM_WITHOUT_COVER && itemType != ITEM_WITH_COVER)
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
        notifyDataSetChanged();
    }

    public void addLeterSeparators() {
        char prevFirstChar = 'a';
        boolean firstSeparator = true;

        for (int i = 0; i < mItems.size(); ++i) {
            String title = mItems.get(i).mTitle;
            char firstChar = title.toUpperCase().charAt(0);

            if (Character.isLetter(firstChar)) {
                if (firstSeparator || firstChar != prevFirstChar) {
                    ListItem item = new ListItem(String.valueOf(firstChar), null, null, true);
                    mItems.add(i, item);
                    i++;
                    prevFirstChar = firstChar;
                    firstSeparator = false;
                }
            }
            else if (firstSeparator) {
                ListItem item = new ListItem("#", null, null, true);
                mItems.add(i, item);
                i++;
                prevFirstChar = firstChar;
                firstSeparator = false;
            }
        }
        notifyDataSetChanged();
    }

    public void addSeparator(String title) {
        ListItem item = new ListItem(title, null, null, true);
        mItems.add(item);
        notifyDataSetChanged();
    }

    /**
     * Remove all the reference to a media in the list items.
     * Remove also all the list items that contain only this media.
     * @param media the media to remove
     */
    public void removeMedia(Media media) {
        for (int i = 0; i < mItems.size(); ++i) {
            ListItem item = mItems.get(i);
            if (item.mMediaList == null)
                continue;
            for (int j = 0; j < item.mMediaList.size(); ++j)
                if (item.mMediaList.get(j).getLocation().equals(media.getLocation())) {
                    item.mMediaList.remove(j);
                    j--;
                }
            if (item.mMediaList.isEmpty() && !item.mIsSeparator) {
                mItems.remove(i);
                i--;
            }
        }
        notifyDataSetChanged();
    }

    public void clear() {
        mMediaItemMap.clear();
        mItems.clear();
        notifyDataSetChanged();
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
            v = inflater.inflate(R.layout.audio_browser_item, parent, false);
            holder = new ViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.cover = (ImageView) v.findViewById(R.id.cover);
            holder.subtitle = (TextView) v.findViewById(R.id.subtitle);
            holder.footer = v.findViewById(R.id.footer);
            holder.more = (ImageView) v.findViewById(R.id.item_more);
            holder.viewType = VIEW_MEDIA;
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        ListItem item = getItem(position);
        holder.title.setText(item.mTitle);

        RelativeLayout.LayoutParams paramsCover;
        if (mItemType == ITEM_WITH_COVER) {
            Media media = mItems.get(position).mMediaList.get(0);
            Bitmap cover = AudioUtil.getCover(v.getContext(), media, 64);
            if (cover == null)
                cover = BitmapCache.GetFromResource(v, R.drawable.icon);
            holder.cover.setImageBitmap(cover);
            int size = (int) mContext.getResources().getDimension(R.dimen.audio_browser_item_size);
            paramsCover = new RelativeLayout.LayoutParams(size, size);
        }
        else
            paramsCover = new RelativeLayout.LayoutParams(0, RelativeLayout.LayoutParams.WRAP_CONTENT);
        holder.cover.setLayoutParams(paramsCover);

        holder.subtitle.setVisibility(item.mSubTitle == null ? TextView.GONE : TextView.VISIBLE);
        holder.subtitle.setText(item.mSubTitle);

        // Remove the footer if the item is just above a separator.
        holder.footer.setVisibility(isMediaItemAboveASeparator(position) ? View.GONE : View.VISIBLE);

        final int pos = position;
        holder.more.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContextPopupMenuListener != null)
                    mContextPopupMenuListener.onPopupMenu(v, pos);
            }
        });

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
            holder.viewType = VIEW_SEPARATOR;
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
        TextView subtitle;
        View footer;
        ImageView more;
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
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return !mItems.get(position).mIsSeparator;
    }

    public ArrayList<Media> getMedia(int position) {
        // Return all the media of a list item list.
        ArrayList<Media> mediaList = new ArrayList<Media>();
        if (!mItems.get(position).mIsSeparator)
            mediaList.addAll(mItems.get(position).mMediaList);
        return mediaList;
    }

    public ArrayList<String> getLocations(int position) {
        // Return all the media locations of a list item list.
        ArrayList<String> locations = new ArrayList<String>();
        if (position < mItems.size() && !mItems.get(position).mIsSeparator) {
            ArrayList<Media> mediaList = mItems.get(position).mMediaList;
            for (int i = 0; i < mediaList.size(); ++i)
                locations.add(mediaList.get(i).getLocation());
        }
        return locations;
    }

    /**
     * Returns a single list containing all media, along with the position of
     * the first media in 'position' in the _new_ single list.
     *
     * @param outputList The list to be written to.
     * @param position Position to retrieve in to _this_ adapter.
     * @return The position of 'position' in the new single list, or 0 if not found.
     */
    public int getListWithPosition(List<String> outputList, int position) {
        int outputPosition = 0;
        outputList.clear();
        for(int i = 0; i < mItems.size(); i++) {
            if(!mItems.get(i).mIsSeparator) {
                if(position == i && !mItems.get(i).mMediaList.isEmpty())
                    outputPosition = outputList.size();

                for(Media k : mItems.get(i).mMediaList) {
                    outputList.add(k.getLocation());
                }
            }
        }
        return outputPosition;
    }

    private boolean isMediaItemAboveASeparator(int position) {
        // Test if a media item if above or not a separator.
        if (mItems.get(position).mIsSeparator)
            throw new IllegalArgumentException("Tested item must be a media item and not a separator.");

        if (position == mItems.size() - 1)
            return false;
        else if (mItems.get(position + 1).mIsSeparator )
            return true;
        else
            return false;
    }

    public interface ContextPopupMenuListener {
        void onPopupMenu(View anchor, final int position);
    }

    void setContextPopupMenuListener(ContextPopupMenuListener l) {
        mContextPopupMenuListener = l;
    }
}
