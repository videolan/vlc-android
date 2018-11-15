/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.tv.audioplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.TvSimpleListItemBinding;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.util.Util;

import java.util.List;

public class PlaylistAdapter extends DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder> {
    public static final String TAG = "VLC/PlaylistAdapter";

    private AudioPlayerActivity audioPlayerActivity;
    private int selectedItem = -1;

    public class ViewHolder extends SelectorViewHolder<TvSimpleListItemBinding> implements View.OnClickListener {
        public ViewHolder(TvSimpleListItemBinding vdb) {
            super(vdb);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            setSelection(getLayoutPosition());
            audioPlayerActivity.playSelection();
        }
    }

    PlaylistAdapter(AudioPlayerActivity audioPlayerActivity) {
        this.audioPlayerActivity = audioPlayerActivity;
    }

    @Override
    public PlaylistAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(TvSimpleListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.binding.setMedia(getDataset().get(position));
        final int textAppearance = position == selectedItem ? R.style.TextAppearance_AppCompat_Title : R.style.TextAppearance_AppCompat_Medium;
        final Context ctx = holder.itemView.getContext();
        holder.binding.artist.setTextAppearance(ctx, textAppearance);
        holder.binding.title.setTextAppearance(ctx, textAppearance);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads)) super.onBindViewHolder(holder, position, payloads);
        else {
            final int textAppearance = (boolean) payloads.get(0) ? R.style.TextAppearance_AppCompat_Title : R.style.TextAppearance_AppCompat_Medium;
            final Context ctx = holder.itemView.getContext();
            holder.binding.artist.setTextAppearance(ctx, textAppearance);
            holder.binding.title.setTextAppearance(ctx, textAppearance);
        }
    }

    int getSelectedItem(){
        return selectedItem;
    }

    public void setSelection(int pos) {
        if (pos == selectedItem) return;
        int previous = selectedItem;
        selectedItem = pos;
        if (previous != -1) notifyItemChanged(previous, false);
        if (pos != -1) notifyItemChanged(selectedItem, true);
    }

    @Override
    protected void onUpdateFinished() {
        audioPlayerActivity.onUpdateFinished();

    }
}