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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.tools.MultiSelectAdapter;
import org.videolan.tools.MultiSelectHelper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.gui.helpers.ImageLoaderKt;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.IListEventsHandler;
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MotionEventCompat;
import androidx.fragment.app.Fragment;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;

import static org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED;

public class AudioBrowserAdapter extends PagedListAdapter<MediaLibraryItem, AudioBrowserAdapter.MediaItemViewHolder> implements FastScroller.SeparatedAdapter, MultiSelectAdapter<MediaLibraryItem>, SwipeDragHelperAdapter {

    private static final String TAG = "VLC/AudioBrowserAdapter";
    private static final int UPDATE_PAYLOAD = 1;

    private final IEventsHandler mIEventsHandler;
    private final IListEventsHandler mListEventsHandler;
    private MultiSelectHelper<MediaLibraryItem> multiSelectHelper;
    private final BitmapDrawable mDefaultCover;
    private final boolean mReorder;
    /**
     * Awful hack to workaround the {@link PagedListAdapter} not keeping track of notifyItemMoved operations
     */
    private static boolean preventNextAnim;

    public AudioBrowserAdapter(int type, IEventsHandler eventsHandler, IListEventsHandler listEventsHandler, boolean canBeReordered) {
        super(DIFF_CALLBACK);
        multiSelectHelper = new MultiSelectHelper<>(this, Constants.UPDATE_SELECTION);
        mIEventsHandler = eventsHandler;
        mListEventsHandler = listEventsHandler;
        mReorder = canBeReordered;
        Context ctx = null;
        if (eventsHandler instanceof Context) ctx = (Context) eventsHandler;
        else if (eventsHandler instanceof Fragment) ctx = ((Fragment)eventsHandler).getContext();
        mDefaultCover = ctx != null ? ImageLoaderKt.getAudioIconDrawable(ctx, type) : null;
    }

    public AudioBrowserAdapter(int type, IEventsHandler eventsHandler) {
        this(type, eventsHandler, null, false);
    }

    @NonNull
    @Override
    public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
        return new MediaItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position) {
        if (position >= getItemCount()) return;
        final MediaLibraryItem item = getItem(position);
        holder.binding.setItem(item);
        final boolean isSelected = multiSelectHelper.isSelected(position);
        holder.setCoverlay(isSelected);
        holder.selectView(isSelected);
        holder.binding.executePendingBindings();
    }

    @Override
    public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (Util.isListEmpty(payloads)) onBindViewHolder(holder, position);
        else {
            final Object payload = payloads.get(0);
            if (payload instanceof MediaLibraryItem) {
                final boolean isSelected = ((MediaLibraryItem) payload).hasStateFlags(FLAG_SELECTED);
                holder.setCoverlay(isSelected);
                holder.selectView(isSelected);
            } else if (payload instanceof Integer) {
                if ((Integer) payload == Constants.UPDATE_SELECTION) {
                    final boolean isSelected = multiSelectHelper.isSelected(position);
                    holder.setCoverlay(isSelected);
                    holder.selectView(isSelected);
                }
            }
        }
    }


    public MultiSelectHelper<MediaLibraryItem> getMultiSelectHelper() {
        return multiSelectHelper;
    }

    @Override
    public void onViewRecycled(@NonNull MediaItemViewHolder holder) {
        if (mDefaultCover != null) holder.binding.setCover(mDefaultCover);
        holder.binding.title.setText("");
        holder.binding.subtitle.setText("");
    }

    private boolean isPositionValid(int position) {
        return position >= 0 && position < getItemCount();
    }

    public boolean isEmpty() {
        final PagedList<MediaLibraryItem> currentList = getCurrentList();
        return currentList == null || currentList.isEmpty();
    }

    @Override
    public long getItemId(int position) {
        if (!isPositionValid(position)) return -1;
        final MediaLibraryItem item = getItem(position);
        return item != null ? item.getId() : -1;
    }

    @Nullable
    @Override
    public MediaLibraryItem getItem(int position) {
        return super.getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        final MediaLibraryItem item = getItem(position);
        return item != null ? item.getItemType() : MediaLibraryItem.TYPE_MEDIA;
    }

    public void clear() {
//        getDataset().clear();
    }


    @Override
    public void onCurrentListChanged(@Nullable PagedList<MediaLibraryItem> previousList, @Nullable PagedList<MediaLibraryItem> currentList) {
        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
    }

    @Override
    public boolean hasSections() {
        return true;
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onItemMoved(int dragFrom, int dragTo) {
        mListEventsHandler.onMove(dragFrom, dragTo);
        preventNextAnim = true;
    }

    @Override
    public void onItemDismiss(int position) {
        final MediaLibraryItem item = getItem(position);
        mListEventsHandler.onRemove(position, item);
    }

    public class MediaItemViewHolder extends SelectorViewHolder<AudioBrowserItemBinding> implements View.OnFocusChangeListener {
        int coverlayResource = 0;
        public View.OnTouchListener onTouchListener;

        @TargetApi(Build.VERSION_CODES.M)
        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            if (mDefaultCover != null) binding.setCover(mDefaultCover);
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener(new View.OnContextClickListener() {
                    @Override
                    public boolean onContextClick(View v) {
                        onMoreClick(v);
                        return true;
                    }
                });

            onTouchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mListEventsHandler == null) {
                        return false;
                    }
                    if (multiSelectHelper.getSelectionCount() != 0) {
                        return false;
                    }
                    if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                        mListEventsHandler.onStartDrag(MediaItemViewHolder.this);
                        return true;
                    }
                    return false;
                }
            };
        }

        public void onClick(View v) {
            int position = getLayoutPosition();
            final MediaLibraryItem item = getItem(position);
            if (item != null) mIEventsHandler.onClick(v, position, item);
        }

        public void onMoreClick(View v) {
            int position = getLayoutPosition();
            final MediaLibraryItem item = getItem(position);
            if (item != null) mIEventsHandler.onCtxClick(v, position, item);
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            final MediaLibraryItem item = getItem(position);
            return item != null && mIEventsHandler.onLongClick(view, position, item);
        }

        public void onImageClick(View v) {
            int position = getLayoutPosition();
            final MediaLibraryItem item = getItem(position);
            if (item != null) mIEventsHandler.onImageClick(v, position, item);
        }

        private void setCoverlay(boolean selected) {
            int resId = selected ? R.drawable.ic_action_mode_select : 0;
            if (resId != coverlayResource) {
                binding.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
                coverlayResource = resId;
            }
        }

        @Override
        protected boolean isSelected() {
            return multiSelectHelper.isSelected(getLayoutPosition());
        }

        public boolean getCanBeReordered() {
            return mReorder;
        }
    }

    private static final DiffUtil.ItemCallback<MediaLibraryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaLibraryItem>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull MediaLibraryItem oldMedia, @NonNull MediaLibraryItem newMedia) {
                    if (preventNextAnim) {
                        return true;
                    }
                    return oldMedia == newMedia || (oldMedia.getItemType() == newMedia.getItemType() && oldMedia.equals(newMedia));
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull MediaLibraryItem oldMedia, @NonNull MediaLibraryItem newMedia) {
                    return false;
                }

                @Override
                public Object getChangePayload(@NotNull MediaLibraryItem oldItem, @NotNull MediaLibraryItem newItem) {
                    preventNextAnim = false;
                    return UPDATE_PAYLOAD;
                }
            };
}

