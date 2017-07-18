/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.audio;

import android.app.Activity;
import android.content.Context;
import android.databinding.ViewDataBinding;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.MainThread;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.BaseQueuedAdapter;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.MediaItemDiffCallback;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.MediaLibraryItemComparator;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class AudioBrowserAdapter extends BaseQueuedAdapter<ArrayList<? extends MediaLibraryItem>, AudioBrowserAdapter.ViewHolder> implements FastScroller.SeparatedAdapter, Filterable {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private boolean mMakeSections = true;

    private ArrayList<? extends MediaLibraryItem> mDataList = new ArrayList<>();
    private ArrayList<? extends MediaLibraryItem> mOriginalDataSet;
    private ItemFilter mFilter = new ItemFilter();
    private Activity mContext;
    private IEventsHandler mIEventsHandler;
    private int mSelectionCount = 0;
    private int mType;
    private BitmapDrawable mDefaultCover;

    public static MediaLibraryItemComparator sMediaComparator = new MediaLibraryItemComparator(MediaLibraryItemComparator.ADAPTER_AUDIO);

    public AudioBrowserAdapter(Activity context, int type, IEventsHandler eventsHandler, boolean sections) {
        mContext = context;
        mIEventsHandler = eventsHandler;
        mMakeSections = sections;
        mType = type;
        mDefaultCover = getIconDrawable();
    }

    public int getAdapterType() {
        return mType;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == MediaLibraryItem.TYPE_DUMMY) {
            AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        } else {
            AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position >= mDataList.size())
            return;
        holder.vdb.setVariable(BR.item, mDataList.get(position));
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            boolean isSelected = mDataList.get(position).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            ((MediaItemViewHolder)holder).setViewBackground(holder.itemView.hasFocus(), isSelected);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads))
            onBindViewHolder(holder, position);
        else {
            boolean isSelected = ((MediaLibraryItem)payloads.get(0)).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            MediaItemViewHolder miv = (MediaItemViewHolder) holder;
            miv.setCoverlay(isSelected);
            miv.setViewBackground(miv.itemView.hasFocus(), isSelected);
        }

    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (mDefaultCover != null)
            holder.vdb.setVariable(BR.cover, mDefaultCover);
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? mDataList.get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 || position < mDataList.size();
    }

    public ArrayList<? extends MediaLibraryItem> getAll() {
        return mDataList;
    }

    ArrayList<MediaLibraryItem> getMediaItems() {
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : mDataList)
            if (!(item.getItemType() == MediaLibraryItem.TYPE_DUMMY)) list.add(item);
        return list;
    }

    int getListWithPosition(ArrayList<MediaLibraryItem> list, int position) {
        int offset = 0, count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (mDataList.get(i).getItemType() == MediaLibraryItem.TYPE_DUMMY) {
                if (i < position)
                    ++offset;
            } else
                list.add(mDataList.get(i));
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        return isPositionValid(position) ? mDataList.get(position).getId() : -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    public boolean hasSections() {
        return mMakeSections;
    }

    @Override
    public String getSectionforPosition(int position) {
        if (mMakeSections)
            for (int i = position; i >= 0; --i)
                if (mDataList.get(i).getItemType() == MediaLibraryItem.TYPE_DUMMY) return mDataList.get(i).getTitle();
        return "";
    }

    @MainThread
    public boolean isEmpty() {
        return (peekLast().size() == 0);
    }

    public void clear() {
        mDataList.clear();
        mOriginalDataSet = null;
    }


    public void addAll(ArrayList<? extends MediaLibraryItem> items) {
        addAll(items, mMakeSections);
    }


    public void addAll(ArrayList<? extends MediaLibraryItem> items, boolean generateSections) {
        if (mContext == null)
            return;
        mDataList = new ArrayList<>(items);
        for (MediaLibraryItem item : mDataList) {
            if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                continue;
            if (item.getTitle().isEmpty()) {
                if (item.getItemType() == MediaLibraryItem.TYPE_ARTIST) {
                    if (item.getId() == 1L)
                        item.setTitle(mContext.getString(R.string.unknown_artist));
                    else if (item.getId() == 2L)
                        item.setTitle(mContext.getString(R.string.various_artists));
                } else if (item.getItemType() == MediaLibraryItem.TYPE_ALBUM) {
                    item.setTitle(mContext.getString(R.string.unknown_album));
                    if (TextUtils.isEmpty(item.getDescription()))
                        item.setDescription(mContext.getString(R.string.unknown_artist));
                }
            } else if (generateSections)
                break;
        }
    }

    public ArrayList<MediaLibraryItem> removeSections(ArrayList<? extends MediaLibraryItem> items) {
        ArrayList<MediaLibraryItem> newList = new ArrayList<>();
        for (MediaLibraryItem item : items)
            if (item.getItemType() != MediaLibraryItem.TYPE_DUMMY)
                newList.add(item);
        return newList;
    }

    private ArrayList<? extends MediaLibraryItem> generateList(ArrayList<? extends MediaLibraryItem> items, int sortby) {
        ArrayList<MediaLibraryItem> datalist = new ArrayList<>();
        switch(sortby) {
            case MediaLibraryItemComparator.SORT_BY_TITLE:
                String currentLetter = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String title = item.getTitle();
                    String letter = (title.isEmpty() || !Character.isLetter(title.charAt(0))) ? "#" : title.substring(0, 1).toUpperCase();
                    if (currentLetter == null || !TextUtils.equals(currentLetter, letter)) {
                        currentLetter = letter;
                        DummyItem sep = new DummyItem(currentLetter);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_LENGTH :
                String currentLengthCategory = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    int length = MediaLibraryItemComparator.getLength(item);
                    String lengthCategory = MediaLibraryItemComparator.lengthToCategory(length);
                    if (currentLengthCategory == null || !TextUtils.equals(currentLengthCategory, lengthCategory)) {
                        currentLengthCategory = lengthCategory;
                        DummyItem sep = new DummyItem(currentLengthCategory);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_DATE :
                String currentYear = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String year = MediaLibraryItemComparator.getYear(item);
                    if (currentYear == null || !TextUtils.equals(currentYear, year)) {
                        currentYear = year;
                        DummyItem sep = new DummyItem(currentYear);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_NUMBER :
                String currentNumber = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    int nb = MediaLibraryItemComparator.getTracksCount(item);
                    String number = (nb == 0) ? "Unknown" : String.valueOf(nb);
                    if (currentNumber == null || !TextUtils.equals(currentNumber, number)) {
                        currentNumber = number;
                        DummyItem sep = new DummyItem(currentNumber);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
        }
        return datalist;
    }

    public void remove(final MediaLibraryItem item) {
        final ArrayList<? extends MediaLibraryItem> referenceList = new ArrayList<>(peekLast());
        if (referenceList.size() == 0) return;
        final ArrayList<? extends MediaLibraryItem> dataList = new ArrayList<>(referenceList);
        dataList.remove(item);
        update(dataList);
    }


    public void addItem(final int position, final MediaLibraryItem item) {
        final ArrayList<? extends MediaLibraryItem> referenceList = peekLast();
        final ArrayList<MediaLibraryItem> dataList = new ArrayList<>(referenceList);
        dataList.add(position,item);
        update(dataList);
    }


    @Override
    public ArrayList<? extends MediaLibraryItem> peekLast() {
        return hasPendingUpdates() ? super.peekLast() : mDataList;
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            update(new ArrayList<>(mOriginalDataSet), false);
            mOriginalDataSet = null;
        }
    }

    protected void internalUpdate(final ArrayList<? extends MediaLibraryItem> items, final boolean detectMoves) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final ArrayList<? extends MediaLibraryItem> newListWithSections = prepareNewList(items);
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new MediaItemDiffCallback(mDataList, newListWithSections), detectMoves);
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        addAll(newListWithSections, false);
                        result.dispatchUpdatesTo(AudioBrowserAdapter.this);
                        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
                        processQueue();
                    }
                });
            }
        });
    }

    private ArrayList<? extends MediaLibraryItem> prepareNewList(final ArrayList<? extends MediaLibraryItem> items) {
        ArrayList<? extends MediaLibraryItem> newListWithSections;
        ArrayList<? extends MediaLibraryItem> newList = removeSections(items);
        Collections.sort(newList, sMediaComparator);
        int realSortby = sMediaComparator.getRealSort(mType);
        newListWithSections = generateList(newList, realSortby);
        return newListWithSections;
    }

    @MainThread
    public List<MediaLibraryItem> getSelection() {
        List<MediaLibraryItem> selection = new LinkedList<>();
        for (MediaLibraryItem item : mDataList)
            if (item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                selection.add(item);
        return selection;
    }

    @MainThread
    public int getSelectionCount() {
        return mSelectionCount;
    }

    @MainThread
    public void resetSelectionCount() {
        mSelectionCount = 0;
    }

    @MainThread
    public void updateSelectionCount(boolean selected) {
        mSelectionCount += selected ? 1 : -1;
    }

    private BitmapDrawable getIconDrawable() {
        switch (mType) {
            case MediaLibraryItem.TYPE_ALBUM:
                return AsyncImageLoader.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_ARTIST:
                return AsyncImageLoader.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                return AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE;
            default:
                return null;
        }
    }

    public class ViewHolder< T extends ViewDataBinding> extends RecyclerView.ViewHolder {
        T vdb;

        public ViewHolder(T vdb) {
            super(vdb.getRoot());
            this.vdb = vdb;
        }

        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public class MediaItemViewHolder extends ViewHolder<AudioBrowserItemBinding> implements View.OnFocusChangeListener {
        int selectionColor = 0, coverlayResource = 0;

        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            itemView.setOnFocusChangeListener(this);
            if (mDefaultCover != null)
                binding.setCover(mDefaultCover);
        }

        public void onClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onClick(v, position, mDataList.get(position));
            }
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onCtxClick(v, position, mDataList.get(position));
            }
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            return mIEventsHandler.onLongClick(view, position, mDataList.get(position));
        }

        private void setCoverlay(boolean selected) {
            int resId = selected ? R.drawable.ic_action_mode_select : 0;
            if (resId != coverlayResource) {
                vdb.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
                coverlayResource = resId;
            }
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus, vdb.getItem().hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        }

        private void setViewBackground(boolean focused, boolean selected) {
            int selectionColor = selected || focused ? UiTools.ITEM_SELECTION_ON : 0;
            if (selectionColor != this.selectionColor) {
                itemView.setBackgroundColor(selectionColor);
                this.selectionColor = selectionColor;
            }
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<? extends MediaLibraryItem> initData() {
            if (mOriginalDataSet == null) {
                mOriginalDataSet = new ArrayList<>(mDataList);
            }
            if (referenceList == null) {
                referenceList = new ArrayList<>(mDataList);
            }
            return referenceList;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update((ArrayList<? extends MediaLibraryItem>) filterResults.values);
        }

    }

    int sortDirection(int sortby) {
        return sMediaComparator.sortDirection(sortby);
    }

    int getSortDirection() {
        return sMediaComparator.sortDirection;
    }

    int getSortBy() {
        return sMediaComparator.sortBy;
    }

    void sortBy(int sortby, int direction) {
        boolean sort;
        switch (sortby){
            case MediaLibraryItemComparator.SORT_BY_LENGTH:
                sort = !((mType == MediaLibraryItem.TYPE_ARTIST) || (mType == MediaLibraryItem.TYPE_GENRE) || (mType == MediaLibraryItem.TYPE_PLAYLIST));
                break;
            case MediaLibraryItemComparator.SORT_BY_DATE:
                sort = !((mType == MediaLibraryItem.TYPE_ARTIST) || (mType == MediaLibraryItem.TYPE_GENRE) || (mType == MediaLibraryItem.TYPE_PLAYLIST));
                break;
            case MediaLibraryItemComparator.SORT_BY_NUMBER:
                sort = !(mType == MediaLibraryItem.TYPE_MEDIA);
                break;
            default:
                sort = true;
                break;
        }
        if (sort) {
            sMediaComparator.sortBy(sortby, direction);
            update(mDataList, true);
        }
    }
}
