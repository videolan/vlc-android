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
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import android.content.Context;
import androidx.databinding.ViewDataBinding;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.tools.MultiSelectAdapter;
import org.videolan.tools.MultiSelectHelper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.ModelsHelper;
import org.videolan.vlc.util.Util;

import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED;

public class AudioBrowserAdapter extends PagedListAdapter<MediaLibraryItem, AudioBrowserAdapter.ViewHolder> implements MultiSelectAdapter<MediaLibraryItem> {

    private static final String TAG = "VLC/AudioBrowserAdapter";
    private static final int UPDATE_PAYLOAD = 1;

    private final IEventsHandler mIEventsHandler;
    private MultiSelectHelper<MediaLibraryItem> multiSelectHelper;
    private final int mType;
    private int mSort;
    private final BitmapDrawable mDefaultCover;

    public AudioBrowserAdapter(int type, IEventsHandler eventsHandler, int sort) {
        super(DIFF_CALLBACK);
        multiSelectHelper = new MultiSelectHelper<>(this, Constants.UPDATE_SELECTION);
        mIEventsHandler = eventsHandler;
        mType = type;
        mDefaultCover = getIconDrawable();
        mSort = sort;
    }

    void setSort(int sort) {
        mSort = sort;
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == MediaLibraryItem.TYPE_DUMMY) {
            final AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder<>(binding);
        } else {
            final AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= getItemCount()) return;
        final MediaLibraryItem item = getItem(position);
        if (item == null) return;
        holder.binding.setVariable(BR.item, getItem(position));
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            setHeader(holder, position, item);
            final boolean isSelected = multiSelectHelper.isSelected(position);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            holder.selectView(isSelected);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (Util.isListEmpty(payloads)) onBindViewHolder(holder, position);
        else {
            final Object payload = payloads.get(0);
            if (payload instanceof MediaLibraryItem) {
                final boolean isSelected = ((MediaLibraryItem)payload).hasStateFlags(FLAG_SELECTED);
                final MediaItemViewHolder miv = (MediaItemViewHolder) holder;
                miv.setCoverlay(isSelected);
                miv.selectView(isSelected);
            } else if (payload instanceof Integer) {
                if ((Integer) payload == UPDATE_PAYLOAD) {
                    final MediaLibraryItem item = getItem(position);
                    if (item == null) return;
                    setHeader(holder, position, item);
                } else if ((Integer) payload == Constants.UPDATE_SELECTION) {
                    final boolean isSelected = multiSelectHelper.isSelected(position);
                    ((MediaItemViewHolder)holder).setCoverlay(isSelected);
                    holder.selectView(isSelected);
                }
            }
        }

    }

    private void setHeader(ViewHolder holder, int position, MediaLibraryItem item) {
        if (mSort == -1) return;
        final MediaLibraryItem aboveItem = position > 0 ? getItem(position-1) : null;
        holder.binding.setVariable(BR.header, ModelsHelper.INSTANCE.getHeader(holder.itemView.getContext(), mSort, item, aboveItem));
    }

    public MultiSelectHelper<MediaLibraryItem> getMultiSelectHelper() {
        return multiSelectHelper;
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (mDefaultCover != null) holder.binding.setVariable(BR.cover, mDefaultCover);
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
        return isPositionValid(position) ? getItem(position).getId() : -1;
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
    public void onCurrentListChanged(@Nullable PagedList<MediaLibraryItem> currentList) {
        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
    }

    private BitmapDrawable getIconDrawable() {
        switch (mType) {
            case MediaLibraryItem.TYPE_ALBUM:
                return UiTools.Resources.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_ARTIST:
                return UiTools.Resources.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                return UiTools.Resources.DEFAULT_COVER_AUDIO_DRAWABLE;
            default:
                return null;
        }
    }

    public class ViewHolder< T extends ViewDataBinding> extends SelectorViewHolder<T> {

        public ViewHolder(T vdb) {
            super(vdb);
            this.binding = vdb;
        }

        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public class MediaItemViewHolder extends ViewHolder<AudioBrowserItemBinding> implements View.OnFocusChangeListener {
        int coverlayResource = 0;

        @TargetApi(Build.VERSION_CODES.M)
        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            if (mDefaultCover != null) binding.setCover(mDefaultCover);
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener(new View.OnContextClickListener() {
                @Override
                public boolean onContextClick(View v) {
                    onMoreClick(v);
                    return true;
                }
            });
        }

        public void onClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onClick(v, position, getItem(position));
            }
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onCtxClick(v, position, getItem(position));
            }
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            return mIEventsHandler.onLongClick(view, position, getItem(position));
        }

        private void setCoverlay(boolean selected) {
            int resId = selected ? R.drawable.ic_action_mode_select : 0;
            if (resId != coverlayResource) {
                binding.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
                coverlayResource = resId;
            }
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        protected boolean isSelected() {
            return multiSelectHelper.isSelected(getLayoutPosition());
        }
    }

    private static final DiffUtil.ItemCallback<MediaLibraryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaLibraryItem>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull MediaLibraryItem oldMedia, @NonNull MediaLibraryItem newMedia) {
                    return oldMedia == newMedia || (oldMedia.getItemType() == newMedia.getItemType() && oldMedia.equals(newMedia));
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull MediaLibraryItem oldMedia, @NonNull MediaLibraryItem newMedia) {
                    return false;
                }

                @Override
                public Object getChangePayload(MediaLibraryItem oldItem, MediaLibraryItem newItem) {
                    return UPDATE_PAYLOAD;
                }
            };
}

