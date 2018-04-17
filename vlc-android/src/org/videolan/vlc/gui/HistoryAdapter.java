/*****************************************************************************
 * HistoryAdapter.java
 *****************************************************************************
 * Copyright © 2012-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.databinding.HistoryItemBinding;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.Util;

import java.util.LinkedList;
import java.util.List;

public class HistoryAdapter extends DiffUtilAdapter<MediaWrapper, HistoryAdapter.ViewHolder> {

    public final static String TAG = "VLC/HistoryAdapter";

    private IEventsHandler mEventsHandler;
    private LayoutInflater mLayoutInflater;

    public class ViewHolder extends SelectorViewHolder<HistoryItemBinding> {

        public ViewHolder(HistoryItemBinding binding) {
            super(binding);
            this.binding = binding;
            binding.setHolder(this);
        }

        public void onClick(View v) {
            int position = getLayoutPosition();
            mEventsHandler.onClick(v, position, getItem(position));
        }

        public boolean onLongClick(View v) {
            int position = getLayoutPosition();
            return mEventsHandler.onLongClick(v, position, getItem(position));
        }

        @Override
        protected boolean isSelected() {
            return getItem(getLayoutPosition()).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
        }
    }

    HistoryAdapter(IEventsHandler eventsHandler) {
        mEventsHandler = eventsHandler;
    }

    List<MediaWrapper> getSelection() {
        final List<MediaWrapper> selection = new LinkedList<>();
        for (MediaWrapper media : getDataset()) {
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                selection.add(media);
        }
        return selection;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (mLayoutInflater == null)
            mLayoutInflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(HistoryItemBinding.inflate(mLayoutInflater, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaWrapper media = getItem(position);
        boolean isSelected = media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
        holder.binding.setMedia(media);
        holder.selectView(isSelected);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads);
        else
            holder.selectView(((MediaLibraryItem) payloads.get(0)).hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
    }

    @Override
    public long getItemId(int arg0) {
        return 0;
    }

    @Override
    public int getItemCount() {
        return getDataset().size();
    }

    @Override
    protected void onUpdateFinished() {}
}