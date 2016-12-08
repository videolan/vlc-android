package org.videolan.vlc.gui.audio;

import android.app.Activity;
import android.content.Context;
import android.databinding.ViewDataBinding;
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
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.MediaItemDiffCallback;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AudioBrowserAdapter extends RecyclerView.Adapter<AudioBrowserAdapter.ViewHolder> implements FastScroller.SeparatedAdapter, Filterable {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private boolean mMakeSections = true;

    private MediaLibraryItem[] mDataList;
    private MediaLibraryItem[] mOriginalDataSet = null;
    private ItemFilter mFilter = new ItemFilter();
    private Activity mContext;
    private IEventsHandler mIEventsHandler;

    public AudioBrowserAdapter(Activity context, IEventsHandler eventsHandler, boolean sections) {
        mContext = context;
        mIEventsHandler = eventsHandler;
        mMakeSections = sections;
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
        if (position >= mDataList.length)
            return;
        holder.vdb.setVariable(BR.item, mDataList[position]);
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            boolean isSelected = mDataList[position].hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            ((MediaItemViewHolder)holder).setViewBackground(((MediaItemViewHolder) holder).itemView.hasFocus(), isSelected);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads);
        else {
            boolean isSelected = ((MediaLibraryItem)payloads.get(0)).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            MediaItemViewHolder miv = (MediaItemViewHolder) holder;
            miv.setCoverlay(isSelected);
            miv.setViewBackground(miv.itemView.hasFocus(), isSelected);
        }

    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.vdb.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE);
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 :  mDataList.length;
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? mDataList[position] : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 || position < mDataList.length;
    }

    public MediaLibraryItem[] getAll() {
        return mDataList;
    }

    ArrayList<MediaLibraryItem> getMediaItems() {
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : mDataList)
            if (!(item.getItemType() == MediaLibraryItem.TYPE_DUMMY))
                list.add(item);
        return list;
    }

    int getListWithPosition(ArrayList<MediaLibraryItem> list, int position) {
        int offset = 0;
        for (int i = 0; i < mDataList.length; ++i)
            if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_DUMMY) {
                if (i < position)
                    ++offset;
            } else
                list.add(mDataList[i]);
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        return isPositionValid(position) ? mDataList[position].getId() : -1;
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
                if (getItem(i).getItemType() == MediaLibraryItem.TYPE_DUMMY)
                    return getItem(i).getTitle();
        return "";
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public void clear() {
        mDataList = null;
        mOriginalDataSet = null;
    }

    public void addAll(MediaLibraryItem[] items) {
        addAll(items, mMakeSections);
    }

    public void addAll(MediaLibraryItem[] items, boolean generateSections) {
        if (mContext == null)
            return;
        mDataList = generateSections ? generateList(items) : items;
    }

    private MediaLibraryItem[] generateList(MediaLibraryItem[] items) {
        ArrayList<MediaLibraryItem> datalist = new ArrayList<>();
        boolean isLetter;
        String currentLetter = null;
        for (MediaLibraryItem item : items) {
            if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                continue;
            String title = item.getTitle();
            if (TextUtils.isEmpty(title))
                continue;
            String firstLetter = title.substring(0, 1).toUpperCase();
            isLetter = Character.isLetter(title.charAt(0));
            if (currentLetter == null) {
                currentLetter = isLetter ? firstLetter : "#";
                DummyItem sep = new DummyItem(currentLetter);
                datalist.add(sep);
            }
            //Add a new separator
            if (isLetter && !TextUtils.equals(currentLetter, firstLetter)) {
                currentLetter = firstLetter;
                DummyItem sep = new DummyItem(currentLetter);
                datalist.add(sep);
            }
            datalist.add(item);
        }
        return datalist.toArray(new MediaLibraryItem[datalist.size()]);
    }

    public void remove(final int position) {
        MediaLibraryItem[] dataList = new MediaLibraryItem[getItemCount()-1];
        int offset = 0;
        for (int i = 0; i < getItemCount(); ++i) {
            if (i == position)
                ++offset;
            else
                dataList[i] = mDataList[i+offset];
        }
        mDataList = dataList;
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemRemoved(position);
            }
        });
    }

    public void addItem(final int position, MediaLibraryItem item) {
        MediaLibraryItem[] dataList = new MediaLibraryItem[getItemCount()+1];
        int offset = 0;
        for (int i = 0; i < getItemCount(); ++i) {
            if (i == position) {
                dataList[position] = item;
                ++offset;
            } else
                dataList[i] = mDataList[i-offset];
        }
        mDataList = dataList;
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(position);
            }
        });
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            dispatchUpdate(Arrays.copyOf(mOriginalDataSet, mOriginalDataSet.length));
            mOriginalDataSet = null;
        }
    }

    void dispatchUpdate(final MediaLibraryItem[] items) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final MediaLibraryItem[] newList = hasSections() ? generateList(items) : items;
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new MediaItemDiffCallback(getAll(), newList));
                mContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        addAll(newList, false);
                        result.dispatchUpdatesTo(AudioBrowserAdapter.this);
                        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
                    }
                });
            }
        });
    }

    List<MediaLibraryItem> getSelection() {
        List<MediaLibraryItem> selection = new LinkedList<>();
        for (MediaLibraryItem media : mDataList)
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                selection.add(media);
        return selection;
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

        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            itemView.setOnFocusChangeListener(this);
            vdb.setCover(AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE);
        }

        public void onClick(View v) {
            if (mIEventsHandler != null)
                mIEventsHandler.onClick(v, getAdapterPosition(), vdb.getItem());
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null)
                mIEventsHandler.onCtxClick(v, getAdapterPosition(), vdb.getItem());
        }

        public boolean onLongClick(View view) {
            return mIEventsHandler.onLongClick(view, getAdapterPosition(), vdb.getItem());
        }

        private void setCoverlay(boolean selected) {
            vdb.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus, vdb.getItem().hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        }

        private void setViewBackground(boolean focused, boolean selected) {
            itemView.setBackgroundColor(focused ? UiTools.ITEM_FOCUS_ON : UiTools.ITEM_FOCUS_OFF);
            int selectionColor = selected ? UiTools.ITEM_SELECTION_ON : 0;
            vdb.audioItemMeta.setBackgroundColor(selectionColor);
            vdb.itemMore.setBackgroundColor(selectionColor);
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaLibraryItem> initData() {
            if (mOriginalDataSet == null)
                mOriginalDataSet = Arrays.copyOf(mDataList, mDataList.length);
            if (referenceList == null)
                referenceList = new ArrayList<>(Arrays.asList(mDataList));
            return referenceList;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            dispatchUpdate(((ArrayList<MediaLibraryItem>) filterResults.values).toArray(new MediaLibraryItem[filterResults.count]));
        }
    }
}
