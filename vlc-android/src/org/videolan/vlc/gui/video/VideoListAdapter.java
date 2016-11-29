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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v4.util.SparseArrayCompat;
import android.support.v7.util.SortedList;
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
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.Strings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder> implements Filterable {

    public final static String TAG = "VLC/VideoListAdapter";

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;

    private final static int TYPE_LIST = 0;
    private final static int TYPE_GRID = 1;

    public final static int SORT_BY_DATE = 2;
    private boolean mListMode = false, mActionMode;
    SparseArrayCompat<WeakReference<ViewHolder>> mHolders = new SparseArrayCompat<>();
    private List<Integer> mSelectedItems = new LinkedList<>();
    private VideoGridFragment mFragment;
    private VideoComparator mVideoComparator = new VideoComparator();
    private volatile SortedList<MediaWrapper> mVideos = new SortedList<>(MediaWrapper.class, mVideoComparator);
    private ArrayList<MediaWrapper> mOriginalData = null;
    private ItemFilter mFilter = new ItemFilter();

    private int mGridCardWidth = 0;
    VideoListAdapter(VideoGridFragment fragment) {
        super();
        mFragment = fragment;
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
        MediaWrapper media = getItem(position);
        if (media == null)
            return;
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.CENTER_CROP);
        fillView(holder, media);
        holder.binding.setVariable(BR.media, media);
        boolean isSelected = mActionMode && mSelectedItems.contains(position);
        holder.setOverlay(mActionMode && isSelected);
        holder.binding.setVariable(BR.bgColor, ContextCompat.getColor(holder.itemView.getContext(), mListMode && isSelected ? R.color.orange200transparent : R.color.transparent));
        mHolders.put(position, new WeakReference<>(holder));
    }


    @Override
    public void onViewRecycled(ViewHolder holder) {
        mHolders.remove(holder.getAdapterPosition());
        holder.binding.setVariable(BR.cover, null);
    }

    @MainThread
    void setTimes( SimpleArrayMap<Long, Long> times) {
        // update times
        for (int i = 0; i < getItemCount(); ++i) {
            MediaWrapper media = mVideos.get(i);
            Long time = times.get(media.getId());
            if (time != null && time != media.getTime()) {
                media.setTime(time);
                int position = getPosition(media);
                WeakReference<ViewHolder> wr = mHolders.get(position);
                if (wr != null && wr.get() != null)
                    fillView(wr.get(), media);
            }
        }
    }

    private int getPosition(MediaWrapper media) {
        for (int i = 0; i < getItemCount(); ++i) {
            if (media.equals(getItem(i)))
                return i;
        }
        return -1;
    }

    public void sort() {
        if (!isEmpty())
            try {
                resetSorting();
            } catch (ArrayIndexOutOfBoundsException e) {} //Exception happening on Android 2.x
    }

    public boolean isEmpty()
    {
        return mVideos.size() == 0;
    }

    @Nullable
    public MediaWrapper getItem(int position) {
        if (position < 0 || position >= mVideos.size())
            return null;
        else
            return mVideos.get(position);
    }

    public void add(MediaWrapper item) {
        int position = mVideos.add(item);
        notifyItemInserted(position);
    }

    @MainThread
    public void remove(int position) {
        if (position == -1)
            return;
        mVideos.removeItemAt(position);
        notifyItemRemoved(position);
    }

    public void addAll(Collection<MediaWrapper> items) {
        mVideos.addAll(items);
        mOriginalData = null;
    }

    public boolean contains(MediaWrapper mw) {
        return mVideos.indexOf(mw) != -1;
    }

    public ArrayList<MediaWrapper> getAll() {
        int size = mVideos.size();
        ArrayList<MediaWrapper> list = new ArrayList<>(size);
        for (int i = 0; i < size; ++i)
            list.add(mVideos.get(i));
        return list;
    }

    List<Integer> getSelectedPositions() {
        return mSelectedItems;
    }

    List<MediaWrapper> getSelection() {
        List<MediaWrapper> selection = new LinkedList<>();
        for (Integer selected : mSelectedItems) {
            MediaWrapper media = mVideos.get(selected);
            if (media instanceof MediaGroup)
                selection.addAll(((MediaGroup)media).getAll());
            else
                selection.add(media);
        }
        return selection;
    }

    @MainThread
    public void update(MediaWrapper item) {
        int position = mVideos.indexOf(item);
        if (position != -1) {
            if (!(mVideos.get(position) instanceof MediaGroup))
                mVideos.updateItemAt(position, item);
            notifyItemChanged(position);
        } else {
            position = mVideos.add(item);
            notifyItemRangeChanged(position, mVideos.size()-position);
        }
    }

    public void clear() {
        mVideos.clear();
        mOriginalData = null;
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text = "";
        String resolution = "";
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
                    text = Strings.millisToText(media.getLength());
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

    public void setGridCardWidth(int gridCardWidth) {
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

    void setActionMode(boolean actionMode) {
        mActionMode = actionMode;
        if (!actionMode) {
            LinkedList<Integer> positions = new LinkedList<>(mSelectedItems);
            mSelectedItems.clear();
            for (Integer position : positions)
                notifyItemChanged(position);
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnFocusChangeListener {
        public ViewDataBinding binding;
        private ImageView thumbView;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
            thumbView = (ImageView) itemView.findViewById(R.id.ml_item_thumbnail);
            binding.setVariable(BR.holder, this);
            itemView.setOnLongClickListener(this);
            itemView.setOnFocusChangeListener(this);
        }

        public void onClick(View v, MediaWrapper media) {
            if (mActionMode) {
                setSelected();
                mFragment.invalidateActionMode();
                return;
            }
            Activity activity = mFragment.getActivity();
            if (media instanceof MediaGroup) {
                String title = media.getTitle().substring(media.getTitle().toLowerCase().startsWith("the") ? 4 : 0);
                ((MainActivity)activity).showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, title);
            } else {
                media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                MediaUtils.openMedia(itemView.getContext(), media);
            }
        }

        public void onMoreClick(View v){
            if (mActionMode || mFragment == null)
                return;
            mFragment.mGridView.openContextMenu(getLayoutPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            if (mActionMode || mFragment == null)
                return false;
            setSelected();
            mFragment.startActionMode();
            return true;
        }

        private void setSelected() {
            Integer position = getLayoutPosition();
            boolean selected = !mSelectedItems.contains(position);
            if (selected)
                mSelectedItems.add(position);
            else
                mSelectedItems.remove(position);
            setOverlay(itemView.hasFocus() || mSelectedItems.contains(position));
            binding.setVariable(BR.bgColor, mListMode && selected ? UiTools.ITEM_SELECTION_ON : UiTools.ITEM_BG_TRANSPARENT);
        }

        private void setOverlay(boolean selected) {
            thumbView.setImageResource(selected ? R.drawable.ic_action_mode_select_1610 : mListMode ? 0 : R.drawable.black_gradient);
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus || mSelectedItems.contains(getLayoutPosition()));
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

    private void resetSorting() {
        ArrayList<MediaWrapper> list = getAll();
        mVideos.clear();
        mVideos.addAll(list);
        notifyItemRangeChanged(0, mVideos.size());
    }

    class VideoComparator extends SortedList.Callback<MediaWrapper> {

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
            resetSorting();

            SharedPreferences.Editor editor = mSettings.edit();
            editor.putInt(KEY_SORT_BY, mSortBy);
            editor.putInt(KEY_SORT_DIRECTION, mSortDirection);
            editor.apply();
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

        @Override
        public void onInserted(int position, int count) {}

        @Override
        public void onRemoved(int position, int count) {}

        @Override
        public void onMoved(int fromPosition, int toPosition) {}

        @Override
        public void onChanged(int position, int count) {}

        @Override
        public boolean areContentsTheSame(MediaWrapper oldItem, MediaWrapper newItem) {
            return areItemsTheSame(oldItem, newItem);
        }

        @Override
        public boolean areItemsTheSame(MediaWrapper item1, MediaWrapper item2) {
            if (item1 == item2)
                return true;
            if (item1 == null ^ item2 == null)
                return false;
            return item1.equals(item2);
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    void restoreList() {
        if (mOriginalData != null) {
            mVideos.clear();
            mVideos.addAll(mOriginalData);
            mOriginalData = null;
            notifyDataSetChanged();
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
            mVideos.clear();
            mVideos.addAll((Collection<MediaWrapper>) filterResults.values);
            notifyDataSetChanged();
        }
    }
    @BindingAdapter({"time", "resolution"})
    public static void setLayoutHeight(View view, String time, String resolution) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = TextUtils.isEmpty(time) && TextUtils.isEmpty(resolution) ?
                layoutParams.MATCH_PARENT :
                layoutParams.WRAP_CONTENT;
        view.setLayoutParams(layoutParams);
    }
}
