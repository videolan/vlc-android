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

import android.content.Context;
import android.databinding.ViewDataBinding;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.MainThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED;
import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_PLAYLIST;

public class AudioBrowserAdapter extends DiffUtilAdapter<MediaLibraryItem, AudioBrowserAdapter.ViewHolder> {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private List<MediaLibraryItem> mOriginalDataSet;
    private final IEventsHandler mIEventsHandler;
    private final int mType;
    private final BitmapDrawable mDefaultCover;
    private int mSelectionCount = 0;

    public AudioBrowserAdapter(int type, IEventsHandler eventsHandler) {
        mIEventsHandler = eventsHandler;
        mType = type;
        mDefaultCover = getIconDrawable();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == MediaLibraryItem.TYPE_DUMMY) {
            final AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder<>(binding);
        } else {
            final AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            // Hide context button for playlist in save playlist dialog
            if (mType == TYPE_PLAYLIST) binding.itemMore.setVisibility(View.GONE);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position >= getDataset().size()) return;
        holder.binding.setVariable(BR.item, getDataset().get(position));
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            final boolean isSelected = getDataset().get(position).hasStateFlags(FLAG_SELECTED);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            holder.selectView(isSelected);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads)) onBindViewHolder(holder, position);
        else {
            final boolean isSelected = ((MediaLibraryItem)payloads.get(0)).hasStateFlags(FLAG_SELECTED);
            final MediaItemViewHolder miv = (MediaItemViewHolder) holder;
            miv.setCoverlay(isSelected);
            miv.selectView(isSelected);
        }

    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (mDefaultCover != null) holder.binding.setVariable(BR.cover, mDefaultCover);
    }

    @Override
    public int getItemCount() {
        return getDataset().size();
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? getDataset().get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 && position < getDataset().size();
    }

    public List<MediaLibraryItem> getAll() {
        return getDataset();
    }

    List<MediaLibraryItem> getMediaItems() {
        final List<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : getDataset()) if (!(item.getItemType() == MediaLibraryItem.TYPE_DUMMY)) list.add(item);
        return list;
    }

    int getListWithPosition(List<MediaLibraryItem> list, int position) {
        int offset = 0, count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (getDataset().get(i).getItemType() == MediaLibraryItem.TYPE_DUMMY) {
                if (i < position)
                    ++offset;
            } else
                list.add(getDataset().get(i));
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        return isPositionValid(position) ? getDataset().get(position).getId() : -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    public void clear() {
        getDataset().clear();
        mOriginalDataSet = null;
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            update(new ArrayList<>(mOriginalDataSet));
            mOriginalDataSet = null;
        }
    }

    @Override
    protected void onUpdateFinished() {
        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
    }

    @MainThread
    public List<MediaLibraryItem> getSelection() {
        final List<MediaLibraryItem> selection = new LinkedList<>();
        for (MediaLibraryItem item : getDataset()) if (item.hasStateFlags(FLAG_SELECTED)) selection.add(item);
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
                mIEventsHandler.onClick(v, position, getDataset().get(position));
            }
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onCtxClick(v, position, getDataset().get(position));
            }
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            return mIEventsHandler.onLongClick(view, position, getDataset().get(position));
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
            return getItem(getLayoutPosition()).hasStateFlags(FLAG_SELECTED);
        }
    }
}
