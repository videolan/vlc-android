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

import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.util.MediaItemFilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder> implements Filterable {

    public final static String TAG = "VLC/VideoListAdapter";

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;

    public final static int SORT_BY_DATE = 2;

    final static int UPDATE_SELECTION = 0;
    final static int UPDATE_THUMB = 1;
    final static int UPDATE_TIME = 2;

    private boolean mListMode = false;
    private IEventsHandler mEventsHandler;
    private VideoComparator mVideoComparator = new VideoComparator();
    private ArrayList<MediaWrapper> mVideos = new ArrayList<>();
    private ArrayDeque<ArrayList<MediaWrapper>> mPendingUpdates = new ArrayDeque<>();
    private ArrayList<MediaWrapper> mOriginalData = null;
    private ItemFilter mFilter = new ItemFilter();
    private int mSelectionCount = 0;
    private int mGridCardWidth = 0;

    VideoListAdapter(IEventsHandler eventsHandler) {
        super();
        mEventsHandler = eventsHandler;
    }

    VideoComparator getComparator() {
        return mVideoComparator;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(mListMode ? R.layout.video_list_card : R.layout.video_grid_card, parent, false);
        if (!mListMode) {
            GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) v.getLayoutParams();
            params.width = mGridCardWidth;
            params.height = params.width*10/16;
            v.setLayoutParams(params);
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MediaWrapper media = mVideos.get(position);
        if (media == null)
            return;
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.CENTER_CROP);
        fillView(holder, media);
        holder.binding.setVariable(BR.media, media);
        boolean isSelected = media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
        holder.setOverlay(isSelected);
        holder.binding.setVariable(BR.bgColor, ContextCompat.getColor(holder.itemView.getContext(), mListMode && isSelected ? R.color.orange200transparent : R.color.transparent));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position);
        else {
            MediaWrapper media = mVideos.get(position);
            for (Object data : payloads) {
                switch ((int) data) {
                    case UPDATE_THUMB:
                        AsyncImageLoader.loadPicture(holder.thumbView, media);
                        break;
                    case UPDATE_TIME:
                        fillView(holder, media);
                        break;
                    case UPDATE_SELECTION:
                        boolean isSelected = media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
                        holder.setOverlay(isSelected);
                        holder.binding.setVariable(BR.bgColor, ContextCompat.getColor(holder.itemView.getContext(), mListMode && isSelected ? R.color.orange200transparent : R.color.transparent));
                        break;
                }
            }
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.binding.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_VIDEO_DRAWABLE);
    }

    public boolean isEmpty()
    {
        return mVideos.size() == 0;
    }

    @Nullable
    public MediaWrapper getItem(int position) {
        return isPositionValid(position) ? mVideos.get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 && position < mVideos.size();
    }

    @MainThread
    public void add(MediaWrapper item) {
        ArrayList<MediaWrapper> list = new ArrayList<>(mPendingUpdates.isEmpty() ? mVideos : mPendingUpdates.peekLast());
        list.add(item);
        update(list, false);
    }

    @MainThread
    public void remove(int position) {
        if (position == -1)
            return;
        ArrayList<MediaWrapper> list = new ArrayList<>(mPendingUpdates.isEmpty() ? mVideos : mPendingUpdates.peekLast());
        list.remove(position);
        update(list, false);
    }

    @MainThread
    public void addAll(Collection<MediaWrapper> items) {
        mVideos.addAll(items);
        mOriginalData = null;
    }

    public boolean contains(MediaWrapper mw) {
        return mVideos.indexOf(mw) != -1;
    }

    public ArrayList<MediaWrapper> getAll() {
        return mVideos;
    }

    List<MediaWrapper> getSelection() {
        List<MediaWrapper> selection = new LinkedList<>();
        for (int i = 0; i < mVideos.size(); ++i) {
            MediaWrapper mw = mVideos.get(i);
            if (mw.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                if (mw instanceof MediaGroup)
                    selection.addAll(((MediaGroup) mw).getAll());
                else
                    selection.add(mw);
            }

        }
        return selection;
    }

    @MainThread
    int getSelectionCount() {
        return mSelectionCount;
    }

    @MainThread
    void resetSelectionCount() {
        mSelectionCount = 0;
    }

    @MainThread
    void updateSelectionCount(boolean selected) {
        mSelectionCount += selected ? 1 : -1;
    }

    @MainThread
    public void update(MediaWrapper item) {
        int position = mVideos.indexOf(item);
        if (position != -1) {
            if (!(mVideos.get(position) instanceof MediaGroup))
                mVideos.set(position, item);
            notifyItemChanged(position, UPDATE_THUMB);
        } else
            add(item);
    }

    @MainThread
    public void clear() {
        mVideos.clear();
        mOriginalData = null;
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text = "";
        String resolution;
        int max = 0;
        int progress = 0;

        if (media.getType() == MediaWrapper.TYPE_GROUP) {
            MediaGroup mediaGroup = (MediaGroup) media;
            int size = mediaGroup.size();
            resolution = VLCApplication.getAppResources().getQuantityString(R.plurals.videos_quantity, size, size);
        } else {
            /* Time / Duration */
            if (media.getLength() > 0) {
                long lastTime = media.getTime();
                if (lastTime > 0) {
                    text = Tools.getProgressText(media);
                    max = (int) (media.getLength() / 1000);
                    progress = (int) (lastTime / 1000);
                } else {
                    text = Tools.millisToText(media.getLength());
                }
            }
            resolution = Tools.getResolution(media);
        }

        holder.binding.setVariable(BR.resolution, resolution);
        holder.binding.setVariable(BR.time, text);
        holder.binding.setVariable(BR.max, max);
        holder.binding.setVariable(BR.progress, progress);
    }

    public void setListMode(boolean value) {
        mListMode = value;
    }

    void setGridCardWidth(int gridCardWidth) {
        mGridCardWidth = gridCardWidth;
    }

    public boolean isListMode() {
        return mListMode;
    }

    @Override
    public long getItemId(int position) {
        return 0L;
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    int getListWithPosition(ArrayList<MediaWrapper>  list, int position) {
        MediaWrapper mw;
        int offset = 0;
        for (int i = 0; i < getItemCount(); ++i) {
            mw = mVideos.get(i);
            if (mw instanceof MediaGroup) {
                for (MediaWrapper item : ((MediaGroup) mw).getAll())
                    list.add(item);
                if (i < position)
                    offset += ((MediaGroup)mw).size()-1;
            } else
                list.add(mw);
        }
        return position+offset;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnFocusChangeListener {
        public ViewDataBinding binding;
        private ImageView thumbView;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
            thumbView = (ImageView) itemView.findViewById(R.id.ml_item_thumbnail);
            binding.setVariable(BR.holder, this);
            binding.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_VIDEO_DRAWABLE);
            itemView.setOnFocusChangeListener(this);
        }

        public void onClick(View v) {
            int position = getAdapterPosition();
            mEventsHandler.onClick(v, position, mVideos.get(position));
        }

        public void onMoreClick(View v){
            mEventsHandler.onCtxClick(v, getAdapterPosition(), null);
        }

        public boolean onLongClick(View v) {
            int position = getAdapterPosition();
            return mEventsHandler.onLongClick(v, position, mVideos.get(position));
        }

        private void setOverlay(boolean selected) {
            thumbView.setImageResource(selected ? R.drawable.ic_action_mode_select_1610 : mListMode ? 0 : R.drawable.black_gradient);
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus || mVideos.get(getAdapterPosition()).hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        }

        private void setViewBackground(boolean highlight) {
            itemView.setBackgroundColor(highlight ? UiTools.ITEM_FOCUS_ON : UiTools.ITEM_FOCUS_OFF);
        }
    }
    int sortDirection(int sortDirection) {
        return mVideoComparator.sortDirection(sortDirection);
    }

    void sortBy(int sortby) {
        mVideoComparator.sortBy(sortby);
    }

    private class VideoComparator implements Comparator<MediaWrapper> {

        private static final String KEY_SORT_BY =  "sort_by";
        private static final String KEY_SORT_DIRECTION =  "sort_direction";

        private int mSortDirection;
        private int mSortBy;
        protected SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());

        VideoComparator() {
            mSortBy = mSettings.getInt(KEY_SORT_BY, SORT_BY_TITLE);
            mSortDirection = mSettings.getInt(KEY_SORT_DIRECTION, 1);
        }

        int sortDirection(int sortby) {
            if (sortby == mSortBy)
                return  mSortDirection;
            else
                return -1;
        }

        void sortBy(int sortby) {
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
                case SORT_BY_DATE:
                    if (mSortBy == SORT_BY_DATE)
                        mSortDirection *= -1;
                    else {
                        mSortBy = SORT_BY_DATE;
                        mSortDirection *= 1;
                    }
                    break;
                default:
                    mSortBy = SORT_BY_TITLE;
                    mSortDirection = 1;
                    break;
            }
            ArrayList<MediaWrapper> list = new ArrayList<>(mVideos);
            update(list, true);

            mSettings.edit()
                    .putInt(KEY_SORT_BY, mSortBy)
                    .putInt(KEY_SORT_DIRECTION, mSortDirection)
                    .apply();
        }

        @Override
        public int compare(MediaWrapper item1, MediaWrapper item2) {
            if (item1 == null)
                return item2 == null ? 0 : -1;
            else if (item2 == null)
                return 1;

            int compare = 0;
            switch (mSortBy) {
                case SORT_BY_TITLE:
                    compare = item1.getTitle().toUpperCase(Locale.ENGLISH).compareTo(
                            item2.getTitle().toUpperCase(Locale.ENGLISH));
                    break;
                case SORT_BY_LENGTH:
                    compare = ((Long) item1.getLength()).compareTo(item2.getLength());
                    break;
                case SORT_BY_DATE:
                    compare = ((Long) item1.getLastModified()).compareTo(item2.getLastModified());
                    break;
            }
            return mSortDirection * compare;
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    @MainThread
    void restoreList() {
        if (mOriginalData != null) {
            update(new ArrayList<>(mOriginalData), false);
            mOriginalData = null;
        }
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaWrapper> initData() {
            if (mOriginalData == null) {
                mOriginalData = new ArrayList<>(mVideos.size());
                for (int i = 0; i < mVideos.size(); ++i)
                    mOriginalData.add(mVideos.get(i));
            }
            return mOriginalData;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            //noinspection unchecked
            update((ArrayList<MediaWrapper>) filterResults.values, false);
        }
    }

    @BindingAdapter({"time", "resolution"})
    public static void setLayoutHeight(View view, String time, String resolution) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = TextUtils.isEmpty(time) && TextUtils.isEmpty(resolution) ?
                ViewGroup.LayoutParams.MATCH_PARENT :
                ViewGroup.LayoutParams.WRAP_CONTENT;
        view.setLayoutParams(layoutParams);
    }

    @MainThread
    void update(final ArrayList<MediaWrapper> items, final boolean detectMoves) {
        mPendingUpdates.add(items);
        if (mPendingUpdates.size() == 1)
            internalUpdate(items, detectMoves);
    }

    private void internalUpdate(final ArrayList<MediaWrapper> items, final boolean detectMoves) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                Collections.sort(items, mVideoComparator);
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new VideoItemDiffCallback(mVideos, items), detectMoves);
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mPendingUpdates.remove();
                        mVideos = items;
                        result.dispatchUpdatesTo(VideoListAdapter.this);
                        mEventsHandler.onUpdateFinished(null);
                        if (!mPendingUpdates.isEmpty())
                            internalUpdate(mPendingUpdates.peek(), true);
                    }
                });
            }
        });
    }

    private class VideoItemDiffCallback extends DiffUtil.Callback {
        ArrayList<MediaWrapper> oldList, newList;
        VideoItemDiffCallback(ArrayList<MediaWrapper> oldList, ArrayList<MediaWrapper> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList == null ? 0 : oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList == null ? 0 : newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList != null && newList != null && oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            MediaWrapper oldItem = oldList.get(oldItemPosition);
            MediaWrapper newItem = newList.get(newItemPosition);
            return oldItem.getTime() == newItem.getTime() && TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl());
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            MediaWrapper oldItem = oldList.get(oldItemPosition);
            MediaWrapper newItem = newList.get(newItemPosition);
            if (oldItem.getTime() != newItem.getTime())
                return UPDATE_TIME;
            else
                return UPDATE_THUMB;
        }
    }
}
