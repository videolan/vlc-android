package org.videolan.vlc.gui.audio;

import android.app.Activity;
import android.content.Context;
import android.databinding.ViewDataBinding;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;

import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.util.MediaItemFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AudioBrowserAdapter extends RecyclerView.Adapter<AudioBrowserAdapter.ViewHolder> implements FastScroller.SeparatedAdapter, Filterable {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private boolean mMakeSections = true, mActionMode;

    public interface EventsHandler {
        void onClick(View v, int position, MediaLibraryItem item);
        void onCtxClick(View v, int position, MediaLibraryItem item);
        void startActionMode();
        void invalidateActionMode();
    }

    private ArrayList<MediaLibraryItem> mDataList = new ArrayList<>();
    private ArrayList<MediaLibraryItem> mOriginalDataSet = null;
    private List<Integer> mSelectedItems = new LinkedList<>();
    private ItemFilter mFilter = new ItemFilter();
    private Activity mContext;
    private EventsHandler mEventsHandler;

    public AudioBrowserAdapter(Activity context, EventsHandler eventsHandler, boolean sections) {
        mContext = context;
        mEventsHandler = eventsHandler;
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
        if (position >= mDataList.size())
            return;
        holder.vdb.setVariable(BR.item, mDataList.get(position));
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            holder.vdb.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE);
            boolean isSelected = mActionMode && mSelectedItems.contains(position);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            ((MediaItemViewHolder)holder).setViewBackground(((MediaItemViewHolder) holder).itemView.hasFocus(), isSelected);
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.vdb.setVariable(BR.cover, null);
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 :  mDataList.size();
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? mDataList.get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 || position < mDataList.size();
    }

    public ArrayList<MediaLibraryItem> getAll() {
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
        for (int i = 0; i < mDataList.size(); ++i)
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
                if (getItem(i).getItemType() == MediaLibraryItem.TYPE_DUMMY)
                    return getItem(i).getTitle();
        return "";
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public void clear() {
        mDataList.clear();
        mOriginalDataSet = null;
        notifyDataSetChanged();
    }

    public void addAll(MediaLibraryItem[] items) {
        if (mContext == null)
            return;
        mOriginalDataSet = null;
        if (mMakeSections) {
            mDataList.clear();
            boolean isLetter;
            String currentLetter = null;
            for (MediaLibraryItem item : items) {
                String title = item.getTitle();
                if (TextUtils.isEmpty(title))
                    continue;
                String firstLetter = title.substring(0, 1).toUpperCase();
                isLetter = Character.isLetter(title.charAt(0));
                if (currentLetter == null) {
                    currentLetter = isLetter ? firstLetter : "#";
                    DummyItem sep = new DummyItem(currentLetter);
                    mDataList.add(sep);
                }
                //Add a new separator
                if (isLetter && !TextUtils.equals(currentLetter, firstLetter)) {
                    currentLetter = firstLetter;
                    DummyItem sep = new DummyItem(currentLetter);
                    mDataList.add(sep);
                }
                    mDataList.add(item);
            }
        } else
            mDataList = new ArrayList<>(Arrays.asList(items));
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    public void remove(final int position) {
        mDataList.remove(position);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemRemoved(position);
            }
        });
    }

    public void addItem(final int position, MediaLibraryItem item) {
        mDataList.add(position, item);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(position);
            }
        });
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            mDataList.clear();
            mDataList.addAll(mOriginalDataSet);
            mOriginalDataSet = null;
            notifyDataSetChanged();
        }
    }

    void setActionMode(boolean actionMode) {
        mActionMode = actionMode;
        if (!actionMode) {
            LinkedList<Integer> positions = new LinkedList<>(mSelectedItems);
            mSelectedItems.clear();
            for (Integer position : positions)
                notifyItemChanged(position);
        }
    }

    List<Integer> getSelectedPositions() {
        return mSelectedItems;
    }

    List<MediaLibraryItem> getSelection() {
        List<MediaLibraryItem> selection = new LinkedList<>();
        for (Integer selected : mSelectedItems) {
            MediaLibraryItem media = mDataList.get(selected);
            selection.add(media);
        }
        return selection;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewDataBinding vdb;

        public ViewHolder(ViewDataBinding vdb) {
            super(vdb.getRoot());
            this.vdb = vdb;
        }

        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public class MediaItemViewHolder extends ViewHolder implements View.OnLongClickListener, View.OnFocusChangeListener {
        private ImageView coverView;
        private View contentLayout, ctxButton;

        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            itemView.setOnLongClickListener(this);
            itemView.setOnFocusChangeListener(this);
            coverView = binding.mediaCover;
            contentLayout = binding.audioItemMeta;
            ctxButton = binding.itemMore;
        }

        public void onClick(View v) {
            if (mActionMode) {
                setSelected();
                if (mEventsHandler != null)
                    mEventsHandler.invalidateActionMode();
                return;
            }
            if (mEventsHandler != null)
                mEventsHandler.onClick(v, getLayoutPosition(), ((AudioBrowserItemBinding)vdb).getItem());
        }

        public void onMoreClick(View v) {
            if (mEventsHandler != null)
                mEventsHandler.onCtxClick(v, getLayoutPosition(), ((AudioBrowserItemBinding)vdb).getItem());
        }

        @Override
        public boolean onLongClick(View view) {
            if (mActionMode)
                return false;
            setSelected();
            mEventsHandler.startActionMode();
            return true;
        }

        private void setSelected() {
            Integer position = getLayoutPosition();
            boolean selected = !mSelectedItems.contains(position);
            if (selected)
                mSelectedItems.add(position);
            else
                mSelectedItems.remove(position);
            setCoverlay(mSelectedItems.contains(position));
            setViewBackground(itemView.hasFocus(), selected);
        }

        private void setCoverlay(boolean selected) {
            coverView.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus, mSelectedItems.contains(getLayoutPosition()));
        }

        private void setViewBackground(boolean focused, boolean selected) {
            itemView.setBackgroundColor(focused ? UiTools.ITEM_FOCUS_ON : UiTools.ITEM_FOCUS_OFF);
            int selectionColor = selected ? UiTools.ITEM_SELECTION_ON : 0;
            contentLayout.setBackgroundColor(selectionColor);
            ctxButton.setBackgroundColor(selectionColor);
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
                mOriginalDataSet = new ArrayList<>(mDataList);
            return mOriginalDataSet;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            mDataList = (ArrayList<MediaLibraryItem>) filterResults.values;
            notifyDataSetChanged();
        }
    }
}
