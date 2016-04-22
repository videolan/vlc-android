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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.support.v4.util.ArrayMap;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;

import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.interfaces.IAudioClickHandler;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AudioBrowserListAdapter extends BaseAdapter implements SectionIndexer, IAudioClickHandler {
    public final static String TAG = "VLC/AudioBrowserListAdapter";

    public final static int TYPE_ARTISTS = 0;
    public final static int TYPE_ALBUMS = 1;
    public final static int TYPE_SONGS = 2;
    public final static int TYPE_GENRES = 3;
    public final static int TYPE_PLAYLISTS = 4;

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
    public static class ListItem {
        final public String mTitle;
        final public String mSubTitle;
        final public ArrayList<MediaWrapper> mMediaList;
        final public boolean mIsSeparator;

        public ListItem(String title, String subTitle, MediaWrapper media, boolean isSeparator) {
            mMediaList = new ArrayList<>();
            if (media != null)
                mMediaList.add(media);
            mTitle = title;
            mSubTitle = subTitle;
            mIsSeparator = isSeparator;
        }
    }

    public AudioBrowserListAdapter(Activity context, int itemType) {
        mMediaItemMap = new ArrayMap<>();
        mSeparatorItemMap = new ArrayMap<>();
        mItems = new ArrayList<>();
        mSections = new SparseArray<>();
        mContext = context;
        if (itemType != ITEM_WITHOUT_COVER && itemType != ITEM_WITH_COVER)
            throw new IllegalArgumentException();
        mItemType = itemType;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        mAlignMode = Integer.valueOf(preferences.getString("audio_title_alignment", "0"));
    }

    public void addAll(final List<ListItem> items) {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (ListItem item : items) {
                    mMediaItemMap.put(item.mTitle, item);
                    mItems.add(item);
                }
                Collections.sort(mItems, mItemsComparator);
            }
        });
    }

    public void add(String title, String subTitle, MediaWrapper media) {
        add(title, subTitle, media, null);
    }

    public void add(String title, String subTitle, MediaWrapper media, String key) {
        if(title == null) return;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        String mediaKey;
        if (key == null)
            mediaKey = (title + subTitle).toLowerCase(Locale.getDefault());
        else
            mediaKey = key.trim().toLowerCase(Locale.getDefault());
        if (mMediaItemMap.containsKey(mediaKey))
            mMediaItemMap.get(mediaKey).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, subTitle, media, false);
            mMediaItemMap.put(mediaKey, item);
            mItems.add(item);
        }
    }

    public void addAll(List<MediaWrapper> mediaList, final int type) {
        final LinkedList<MediaWrapper> list = new LinkedList<MediaWrapper>(mediaList);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clear();
                String title, subTitle, key;
                for (MediaWrapper media : list) {
                    switch (type){
                        case TYPE_ALBUMS:
                            title = MediaUtils.getMediaAlbum(mContext, media);
                            subTitle = MediaUtils.getMediaReferenceArtist(mContext, media);
                            key = title.trim();
                            break;
                        case TYPE_ARTISTS:
                            title = MediaUtils.getMediaReferenceArtist(mContext, media);
                            subTitle = null;
                            key = title.trim();
                            break;
                        case TYPE_GENRES:
                            title = MediaUtils.getMediaGenre(mContext, media);
                            subTitle = null;
                            key = title.trim();
                            break;
                        case TYPE_PLAYLISTS:
                            title = media.getTitle();
                            subTitle = null;
                            key = null;
                            break;
                        case TYPE_SONGS:
                            title = media.getTitle();
                            subTitle = MediaUtils.getMediaArtist(mContext, media);
                            key = media.getAlbum() + media.getArtist() + media.getLocation();
                            break;
                        default:
                            title = media.getTitle();
                            subTitle = MediaUtils.getMediaArtist(mContext, media);
                            key = media.getLocation();
                    }
                    add(title, subTitle, media, key);
                }
                calculateSections(type);
            }
        });
    }

    public void remove(int position, String key) {
        mItems.remove(position);
        mMediaItemMap.remove(key);
        notifyDataSetChanged();
    }

    public void addItem(int position, String key, ListItem item) {
        mMediaItemMap.put(key, item);
        mItems.add(position, item);
        notifyDataSetChanged();
    }

    public String getKey(int position) {
        return (String) mMediaItemMap.keySet().toArray()[position];
    }

    /**
     * Calculate sections of the list
     *
     * @param type Type of the audio file sort.
     */
    private void calculateSections(int type) {
        char prevFirstChar = '%';
        boolean firstSeparator = true;
        ArrayList<String> sections = new ArrayList<>();

        for (int i = 0; i < mItems.size(); ++i) {
            String title = mItems.get(i).mTitle;
            String unknown;
            switch (type){
                case TYPE_ALBUMS:
                    unknown = mContext.getString(R.string.unknown_album);
                    break;
                case TYPE_GENRES:
                    unknown = mContext.getString(R.string.unknown_genre);
                    break;
                case TYPE_ARTISTS:
                    unknown = mContext.getString(R.string.unknown_artist);
                    break;
                default:
                    unknown = null;
            }
            char firstChar;
            if(title.length() > 0 && (unknown == null || !unknown.equals(title)))
                firstChar = title.toUpperCase(Locale.ENGLISH).charAt(0);
            else
                firstChar = '#'; // Blank / spaces-only song title.

            if (Character.isLetter(firstChar)) {
                String firstCharInString = String.valueOf(firstChar);
                if ((firstSeparator || firstChar != prevFirstChar) && !sections.contains(firstCharInString)) {
                    ListItem item = new ListItem(firstCharInString, null, null, true);
                    mItems.add(i, item);
                    mSections.put(i, String.valueOf(firstChar));
                    i++;
                    prevFirstChar = firstChar;
                    firstSeparator = false;
                    sections.add(firstCharInString);
                }
            } else if (firstSeparator) {
                ListItem item = new ListItem("#", null, null, true);
                mItems.add(i, item);
                mSections.put(i, "#");
                i++;
                prevFirstChar = firstChar;
                firstSeparator = false;
            }
        }
    }

    public void addSeparator(String title, MediaWrapper media) {
        if(title == null) return;
        title = title.trim();
        final String titleKey = title.toLowerCase(Locale.getDefault());
        if (mSeparatorItemMap.containsKey(titleKey))
            mSeparatorItemMap.get(titleKey).mMediaList.add(media);
        else {
            ListItem item = new ListItem(title, null, media, true);
            mSeparatorItemMap.put(titleKey, item);
            mItems.add(item);
        }
    }

    public void sortByAlbum(){
        mItems.clear();
        for (ListItem album : mSeparatorItemMap.values()){
            mItems.add(album);
            Collections.sort(album.mMediaList, MediaComparators.byTrackNumber);
            for (MediaWrapper media : album.mMediaList)
                add(media.getTitle(), null, media, media.getLocation());
        }
    }

    /**
     * Remove all the reference to a media in the list items.
     * Remove also all the list items that contain only this media.
     * @param media the media to remove
     */
    public void removeMedia(MediaWrapper media, boolean notify) {
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
        if (notify)
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
        ViewHolder holder = null;

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

            holder = new ViewHolder();
            holder.binding = DataBindingUtil.inflate(inflater, R.layout.audio_browser_item, parent, false);
            v = holder.binding.getRoot();
            holder.viewType = VIEW_MEDIA;
            v.setTag(holder);
        }

        ListItem item = getItem(position);
        holder.binding.setVariable(BR.alignMode, mAlignMode);
        holder.binding.setVariable(BR.item, item);
        holder.binding.setVariable(BR.position, position);

        if (mItemType == ITEM_WITH_COVER) {
            //Tagging the binding to the ImageView will trigger async image loading with:
            // org.videolan.vlc.gui.helpers.AsyncImageLoader.loadPicture(ImageView , AudioBrowserListAdapter.ListItem)
            holder.binding.getRoot().findViewById(R.id.media_cover).setTag(holder.binding);
        } else
            holder.binding.setVariable(BR.cover, AudioUtil.DEFAULT_COVER);

        holder.binding.setVariable(BR.hasFooter, !isMediaItemAboveASeparator(position));
        holder.binding.setVariable(BR.clickable, mContextPopupMenuListener != null);
        holder.binding.setVariable(BR.handler, this);
        holder.binding.executePendingBindings();

        return v;
    }

    public View getViewSeparator(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder holder = null;

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
            holder = new ViewHolder();
            holder.binding = DataBindingUtil.inflate(inflater, R.layout.audio_browser_separator, parent, false);
            v = holder.binding.getRoot();
            holder.viewType = VIEW_SEPARATOR;
            v.setTag(holder);
        }

        ListItem item = getItem(position);
        holder.binding.setVariable(BR.item, item);
        holder.binding.executePendingBindings();

        return v;
    }

    static class ViewHolder {
        int viewType;
        ViewDataBinding binding;
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

    public ArrayList<MediaWrapper> getMedias(int position) {
        // Return all the media of a list item list.
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
        ListItem item = mItems.get(position);
        if (!item.mIsSeparator || !item.mMediaList.isEmpty())
            mediaList.addAll(item.mMediaList);
        return mediaList;
    }

    public ArrayList<MediaWrapper> getMedias(int position, boolean sortByTrackNumber) {
        ArrayList<MediaWrapper> mediaList = getMedias(position);
        if (isEnabled(position)) {
            if (sortByTrackNumber)
                Collections.sort(mediaList, MediaComparators.byTrackNumber);
        }
        return mediaList;
    }

    public String getTitle(int position) {
        return getItem(position).mTitle;
    }

//    public ArrayList<String> getLocations(int position) {
//        return getLocations(position, false);
//    }
//
//    public ArrayList<String> getLocations(int position, boolean sortByTrackNumber) {
//        // Return all the media locations of a list item list.
//        ArrayList<String> locations = new ArrayList<String>();
//        if (isEnabled(position)) {
//            ArrayList<MediaWrapper> mediaList = mItems.get(position).mMediaList;
//            if (sortByTrackNumber)
//                Collections.sort(mediaList, MediaComparators.byTrackNumber);
//            for (int i = 0; i < mediaList.size(); ++i)
//                locations.add(mediaList.get(i).getLocation());
//        }
//        return locations;
//    }

    /**
     * Returns a single list containing all media, along with the position of
     * the first media in 'position' in the _new_ single list.
     *
     * @param outputList The list to be written to.
     * @param position Position to retrieve in to _this_ adapter.
     * @return The position of 'position' in the new single list, or 0 if not found.
     */
    public int getListWithPosition(List<MediaWrapper> outputList, int position) {
        int outputPosition = 0;
        outputList.clear();
        for(int i = 0; i < mItems.size(); i++) {
            if(!mItems.get(i).mIsSeparator) {
                if(position == i && !mItems.get(i).mMediaList.isEmpty())
                    outputPosition = outputList.size();

                for(MediaWrapper mediaWrapper : mItems.get(i).mMediaList) {
                    outputList.add(mediaWrapper);
                }
            }
        }
        return outputPosition;
    }

    private boolean isMediaItemAboveASeparator(int position) {
        // Test if a media item if above or not a separator.
        if (mItems.get(position).mIsSeparator)
            throw new IllegalArgumentException("Tested item must be a media item and not a separator.");

        //consider end of list as a separator. Nicer to display
        return (position == mItems.size() - 1 || mItems.get(position + 1).mIsSeparator);
    }

    public interface ContextPopupMenuListener {
        void onPopupMenu(View anchor, final int position);
    }

    void setContextPopupMenuListener(ContextPopupMenuListener l) {
        mContextPopupMenuListener = l;
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null)
            super.unregisterDataSetObserver(observer);
    }

    private Comparator<ListItem> mItemsComparator = new Comparator<ListItem>() {
        @Override
        public int compare(ListItem lhs, ListItem rhs) {
            return String.CASE_INSENSITIVE_ORDER.compare(lhs.mTitle, rhs.mTitle);
        }
    };

    @Override
    public void onMoreClick(View v) {
        if (mContextPopupMenuListener != null)
            mContextPopupMenuListener.onPopupMenu(v, ((Integer)v.getTag()).intValue());
    }
}
