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

import android.databinding.DataBindingUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.ListItemBinding;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public final static String TAG = "VLC/HistoryAdapter";

    private ArrayList<MediaWrapper> mMediaList = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder {
        ListItemBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
        }

        public void onClick(View v){
            int position = getAdapterPosition();
            MediaWrapper mw = mMediaList.get(position);

            if (position != 0) {
                mMediaList.remove(position);
                mMediaList.add(0, mw);
                notifyItemMoved(position, 0);
            }
            MediaUtils.openMedia(v.getContext(), mw);
        }
    }

    public void setList(ArrayList<MediaWrapper> list) {
        mMediaList = list;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaWrapper media = mMediaList.get(position);
        holder.binding.setMedia(media);
        holder.binding.setHolder(holder);
    }

    @Override
    public long getItemId(int arg0) {
        return 0;
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public boolean isEmpty() {
        return mMediaList.isEmpty();
    }

    public void clear() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                MediaDatabase.getInstance().clearHistory();
            }
        });
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public void remove(final int position) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                MediaDatabase.getInstance().deleteHistoryUri(mMediaList.get(position).getUri().toString());
            }
        });
        mMediaList.remove(position);
        notifyItemRemoved(position);
    }
}
