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
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.SortableAdapter;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.util.MediaItemDiffCallback;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.MediaLibraryItemComparator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class VideoListAdapter extends SortableAdapter<MediaWrapper, VideoListAdapter.ViewHolder> implements Filterable {

    public final static String TAG = "VLC/VideoListAdapter";

    final static int UPDATE_SELECTION = 0;
    final static int UPDATE_THUMB = 1;
    final static int UPDATE_TIME = 2;
    final static int UPDATE_SEEN = 3;

    private boolean mListMode = false;
    private IEventsHandler mEventsHandler;
    private List<MediaWrapper> mOriginalData = null;
    private final ItemFilter mFilter = new ItemFilter();
    private int mSelectionCount = 0;
    private int mGridCardWidth = 0;

    private boolean mIsSeenMediaMarkerVisible = true;

    VideoListAdapter(IEventsHandler eventsHandler) {
        super();
        mEventsHandler = eventsHandler;
        final SharedPreferences settings = VLCApplication.getSettings();
        mIsSeenMediaMarkerVisible = settings == null || settings.getBoolean("media_seen", true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewDataBinding binding = DataBindingUtil.inflate(inflater, mListMode ? R.layout.video_list_card : R.layout.video_grid_card, parent, false);
        if (!mListMode) {
            GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) binding.getRoot().getLayoutParams();
            params.width = mGridCardWidth;
            params.height = params.width*10/16;
            binding.getRoot().setLayoutParams(params);
        }
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaWrapper media = getDataset().get(position);
        if (media == null)
            return;
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.CENTER_CROP);
        fillView(holder, media);
        holder.binding.setVariable(BR.media, media);
        holder.selectView(media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position);
        else {
            final MediaWrapper media = getDataset().get(position);
            for (Object data : payloads) {
                switch ((int) data) {
                    case UPDATE_THUMB:
                        AsyncImageLoader.loadPicture(holder.thumbView, media);
                        break;
                    case UPDATE_TIME:
                    case UPDATE_SEEN:
                        fillView(holder, media);
                        break;
                    case UPDATE_SELECTION:
                        holder.selectView(media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
                        break;
                }
            }
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.binding.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_VIDEO_DRAWABLE);
    }

    public boolean isEmpty() {
        return peekLast().size() == 0;
    }

    @Nullable
    public MediaWrapper getItem(int position) {
        return isPositionValid(position) ? getDataset().get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 && position < getDataset().size();
    }

    @MainThread
    public void add(MediaWrapper item) {
        final List<MediaWrapper> list = new ArrayList<>(peekLast());
        list.add(item);
        //Force adapter to sort items.
        if (sMediaComparator.sortBy == MediaLibraryItemComparator.SORT_DEFAULT) sMediaComparator.sortBy = getDefaultSort();
        update(list);
    }

    @MainThread
    public int remove(MediaWrapper item) {
        final List<MediaWrapper> refList = new ArrayList<>(peekLast());
        final int position = refList.indexOf(item);
        if (position < 0 || position >= refList.size()) return -1;
        refList.remove(position);
        update(refList);
        return position;
    }

    public boolean contains(MediaWrapper mw) {
        return getDataset().indexOf(mw) != -1;
    }

    public List<MediaWrapper> getAll() {
        return getDataset();
    }

    List<MediaWrapper> getSelection() {
        final List<MediaWrapper> selection = new LinkedList<>();
        for (int i = 0; i < getDataset().size(); ++i) {
            MediaWrapper mw = getDataset().get(i);
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
    public void clear() {
        update(new ArrayList<MediaWrapper>());
        mOriginalData = null;
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text = "";
        String resolution = "";
        int max = 0;
        int progress = 0;
        long seen = 0L;

        if (media.getType() == MediaWrapper.TYPE_GROUP) {
            MediaGroup mediaGroup = (MediaGroup) media;
            final int size = mediaGroup.size();
            text = VLCApplication.getAppResources().getQuantityString(R.plurals.videos_quantity, size, size);
        } else {
            /* Time / Duration */
            if (media.getLength() > 0) {
                final long lastTime = media.getTime();
                if (lastTime > 0) {
                    text = Tools.getProgressText(media);
                    max = (int) (media.getLength() / 1000);
                    progress = (int) (lastTime / 1000);
                } else {
                    text = Tools.millisToText(media.getLength());
                }
            }
            resolution = Tools.getResolution(media);
            seen = mIsSeenMediaMarkerVisible ? media.getSeen() : 0L;
        }

        holder.binding.setVariable(BR.resolution, resolution);
        holder.binding.setVariable(BR.time, text);
        holder.binding.setVariable(BR.max, max);
        holder.binding.setVariable(BR.progress, progress);
        holder.binding.setVariable(BR.seen, seen);
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
        return getDataset().size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    int getListWithPosition(List<MediaWrapper>  list, int position) {
        MediaWrapper mw;
        int offset = 0;
        for (int i = 0; i < getItemCount(); ++i) {
            mw = getDataset().get(i);
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

    public class ViewHolder extends SelectorViewHolder<ViewDataBinding> implements View.OnFocusChangeListener {
        private ImageView thumbView;

        public ViewHolder(ViewDataBinding binding) {
            super(binding);
            thumbView = itemView.findViewById(R.id.ml_item_thumbnail);
            binding.setVariable(BR.holder, this);
            binding.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_VIDEO_DRAWABLE);
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener(new View.OnContextClickListener() {
                @Override
                public boolean onContextClick(View v) {
                    onMoreClick(v);
                    return true;
                }
            });
        }

        public void onClick(View v) {
            final int position = getLayoutPosition();
            if (isPositionValid(position))
                mEventsHandler.onClick(v, position, getDataset().get(position));
        }

        public void onMoreClick(View v){
            final int position = getLayoutPosition();
            if (isPositionValid(position))
                mEventsHandler.onCtxClick(v, position, null);
        }

        public boolean onLongClick(View v) {
            final int position = getLayoutPosition();
            return isPositionValid(position) && mEventsHandler.onLongClick(v, position, getDataset().get(position));
        }

        @Override
        public void selectView(boolean selected) {
            thumbView.setImageResource(selected ? R.drawable.ic_action_mode_select_1610 : mListMode ? 0 : R.drawable.black_gradient);
            super.selectView(selected);
        }

        @Override
        protected boolean isSelected() {
            return getDataset().get(getLayoutPosition()).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    @MainThread
    void restoreList() {
        if (mOriginalData != null) {
            update(new ArrayList<>(mOriginalData));
            mOriginalData = null;
        }
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaWrapper> initData() {
            if (mOriginalData == null) {
                mOriginalData = new ArrayList<>(getDataset().size());
                for (int i = 0; i < getDataset().size(); ++i)
                    mOriginalData.add(getDataset().get(i));
            }
            return mOriginalData;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            //noinspection unchecked
            update((List<MediaWrapper>) filterResults.values);
        }
    }

    @BindingAdapter({"time", "resolution"})
    public static void setLayoutHeight(View view, String time, String resolution) {
        final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = TextUtils.isEmpty(time) && TextUtils.isEmpty(resolution) ?
                ViewGroup.LayoutParams.MATCH_PARENT :
                ViewGroup.LayoutParams.WRAP_CONTENT;
        view.setLayoutParams(layoutParams);
    }

    @Override
    protected MediaItemDiffCallback<MediaWrapper> createCB() {
        return new VideoItemDiffCallback();
    }

    @Override
    protected void onUpdateFinished() {
        super.onUpdateFinished();
        mEventsHandler.onUpdateFinished(null);
    }

    private class VideoItemDiffCallback extends MediaItemDiffCallback<MediaWrapper> {

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            return oldItem == newItem || (oldItem.getType() == newItem.getType() && oldItem.equals(newItem));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            return oldItem == newItem || (oldItem.getTime() == newItem.getTime()
                    && TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl())
                    && oldItem.getSeen() == newItem.getSeen());
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            if (oldItem.getTime() != newItem.getTime())
                return UPDATE_TIME;
            if (!TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl()))
                return UPDATE_THUMB;
            else
                return UPDATE_SEEN;
        }
    }

    void setSeenMediaMarkerVisible(boolean seenMediaMarkerVisible) {
        mIsSeenMediaMarkerVisible = seenMediaMarkerVisible;
    }

    @Override
    protected boolean isSortAllowed(int sort) {
        switch (sort) {
            case MediaLibraryItemComparator.SORT_BY_TITLE:
            case MediaLibraryItemComparator.SORT_BY_DATE:
            case MediaLibraryItemComparator.SORT_BY_LENGTH:
                return true;
            default:
                return false;
        }
    }
}
