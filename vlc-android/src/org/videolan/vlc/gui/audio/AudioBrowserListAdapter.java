/*****************************************************************************
 * AudioBrowserListAdapter.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.BitmapCache;
import org.videolan.vlc.util.Util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

public class AudioBrowserListAdapter extends BaseAdapter implements SectionIndexer {
    public final static String TAG = "VLC/AudioBrowserListAdapter";

    public final static int TYPE_ARTISTS = 0;
    public final static int TYPE_ALBUMS = 1;
    public final static int TYPE_SONGS = 2;
    public final static int TYPE_GENRES = 3;

    // Key: the item title, value: ListItem of only media item (no separator).
    private Map<String, ListItem> mMediaItemMap;
    private Map<String, ListItem> mSeparatorItemMap;
    // A list of all the list items: media items and separators.
    private ArrayList<ListItem> mItems;
    // A list of all the sections in the list; better performance than searching the whole list
    private SparseArray<String> mSections;

    private int mAlignMode; // align mode from prefs

    private Activity mContext;

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
        public ArrayList<MediaWrapper> mMediaList;
        public boolean mIsSeparator;

        public ListItem(String title, String subTitle, MediaWrapper media, boolean isSeparator) {
            mMediaList = new ArrayList<MediaWrapper>();
            if (media != null)
                mMediaList.add(media);
            mTitle = title;
            mSubTitle = subTitle;
            mIsSeparator = isSeparator;
        }
    }

    public AudioBrowserListAdapter(Activity context, int itemType) {
        mMediaItemMap = new HashMap<String, ListItem>();
        mSeparatorItemMap = new HashMap<String, ListItem>();
        mItems = new ArrayList<ListItem>();
        mSections = new SparseArray<String>();
        mContext = context;
        if (itemType != ITEM_WITHOUT_COVER && itemType != ITEM_WITH_COVER)
            throw new IllegalArgumentException();
        mItemType = itemType;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        mAlignMode = Integer.valueOf(preferences.getString("audio_title_alignment", "0"));
    }

    public void add(String title, String subTitle, MediaWrapper media) {
        if(title == null) return;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, subTitle, media, false);
            mMediaItemMap.put(title, item);
            mItems.add(item);
        }
    }

    public void addAll(List<MediaWrapper> mediaList, final int type) {
        final LinkedList<MediaWrapper> list = new LinkedList<MediaWrapper>(mediaList);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String title, subTitle;
                for (MediaWrapper media : list) {
                    switch (type){
                        case TYPE_ALBUMS:
                            title = Util.getMediaAlbum(mContext, media);
                            subTitle = Util.getMediaReferenceArtist(mContext, media);
                            break;
                        case TYPE_ARTISTS:
                            title = Util.getMediaReferenceArtist(mContext, media);
                            subTitle = null;
                            break;
                        case TYPE_GENRES:
                            title = Util.getMediaGenre(mContext, media);
                            subTitle = null;
                            break;
                        case TYPE_SONGS:
                        default:
                            title = media.getTitle();
                            subTitle = Util.getMediaArtist(mContext, media);
                    }
                    if (title == null)
                        continue;
                    title = title.trim();
                    if (subTitle != null) subTitle = subTitle.trim();
                    if (mMediaItemMap.containsKey(title))
                        mMediaItemMap.get(title).mMediaList.add(media);
                    else {
                        ListItem item = new ListItem(title, subTitle, media, false);
                        mMediaItemMap.put(title, item);
                        mItems.add(item);
                    }
                }
                addLetterSeparators();
            }
        });
    }

    public void addLetterSeparators() {
        calculateSections(true);
    }

    public void addScrollSections() {
        calculateSections(false);
    }

    /**
     * Calculate sections of the list
     *
     * @param addSeparators True to add GUI-level separators, false to just populate the cache
     */
    private void calculateSections(boolean addSeparators) {
        char prevFirstChar = 'a';
        boolean firstSeparator = true;

        for (int i = 0; i < mItems.size(); ++i) {
            String title = mItems.get(i).mTitle;
            char firstChar;
            if(title.length() > 0)
                firstChar = title.toUpperCase(Locale.ENGLISH).charAt(0);
            else
                firstChar = '#'; // Blank / spaces-only song title.

            if (Character.isLetter(firstChar)) {
                if (firstSeparator || firstChar != prevFirstChar) {
                    if(addSeparators) {
                        ListItem item = new ListItem(String.valueOf(firstChar), null, null, true);
                        mItems.add(i, item);
                        mSections.put(i, String.valueOf(firstChar));
                        i++;
                    } else
                        mSections.put(i, String.valueOf(firstChar));
                    prevFirstChar = firstChar;
                    firstSeparator = false;
                }
            }
            else if (firstSeparator) {
                if(addSeparators) {
                    ListItem item = new ListItem("#", null, null, true);
                    mItems.add(i, item);
                    mSections.put(i, "#");
                    i++;
                } else
                    mSections.put(i, "#");
                prevFirstChar = firstChar;
                firstSeparator = false;
            }
        }
    }

    public void addSeparator(String title, MediaWrapper media) {
        if(title == null) return;
        title = title.trim();
        if (mSeparatorItemMap.containsKey(title))
            mSeparatorItemMap.get(title).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, null, media, true);
            mSeparatorItemMap.put(title, item);
            mItems.add(item);
        }
    }

    public void sortByAlbum(){
        mItems.clear();
        for (ListItem album : mSeparatorItemMap.values()){
            mItems.add(album);
            Collections.sort(album.mMediaList, MediaComparators.byTrackNumber);
            for (MediaWrapper media : album.mMediaList)
                add(media.getTitle(), null, media);
        }
    }

    /**
     * Remove all the reference to a media in the list items.
     * Remove also all the list items that contain only this media.
     * @param media the media to remove
     */
    public void removeMedia(MediaWrapper media) {
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
        mSeparatorItemMap.clear();
        mItems.clear();
        mSections.clear();
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
            Util.setAlignModeByPref(mAlignMode, holder.title);
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
            MediaWrapper media = mItems.get(position).mMediaList.get(0);
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

    static class ViewHolder {
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
        int viewType = VIEW_MEDIA;
        if (mItems.get(position).mIsSeparator)
            viewType = VIEW_SEPARATOR;
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
        return position < mItems.size() && mItems.get(position).mMediaList.size() > 0;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        int index;
        if(mSections.size() == 0)
            index = 0;
        else if(sectionIndex >= mSections.size())
            index = mSections.size() - 1;
        else if(sectionIndex <= 0)
            index = 0;
        else
            index = sectionIndex;
        return mSections.keyAt(index);
    }

    @Override
    public int getSectionForPosition(int position) {
        for(int i = 0; i < mSections.size(); i++) {
            if(position > mSections.keyAt(i))
                return i;
        }
        return mSections.size()-1; // default to last section
    }

    @Override
    public Object[] getSections() {
        ArrayList<String> sections = new ArrayList<String>();
        for(int i = 0; i < mSections.size(); i++) {
            sections.add(mSections.valueAt(i));
        }
        return sections.toArray();
    }

    public ArrayList<MediaWrapper> getMedia(int position) {
        // Return all the media of a list item list.
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
        if (!mItems.get(position).mIsSeparator)
            mediaList.addAll(mItems.get(position).mMediaList);
        return mediaList;
    }

    public ArrayList<String> getLocations(int position) {
        return getLocations(position, false);
    }

    public ArrayList<String> getLocations(int position, boolean sortByTrackNumber) {
        // Return all the media locations of a list item list.
        ArrayList<String> locations = new ArrayList<String>();
        if (isEnabled(position)) {
            ArrayList<MediaWrapper> mediaList = mItems.get(position).mMediaList;
            if (sortByTrackNumber)
                Collections.sort(mediaList, MediaComparators.byTrackNumber);
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

                for(MediaWrapper k : mItems.get(i).mMediaList) {
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
            return true; //consider end of list as a separator. Nicer to display
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
