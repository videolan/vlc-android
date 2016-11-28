/*****************************************************************************
 * MRLAdapter.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.network;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.medialibrary.media.HistoryItem;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaUtils;

class MRLAdapter extends RecyclerView.Adapter<MRLAdapter.ViewHolder> {
    private HistoryItem[] mDataset;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView uriTv, titleTv;

        public ViewHolder(View v) {
            super(v);
            uriTv = (TextView) v.findViewById(R.id.mrl_item_uri);
            titleTv = (TextView) v.findViewById(R.id.mrl_item_title);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
                UiTools.setKeyboardVisibility(itemView, false);
                MediaUtils.openMedia(v.getContext(), mDataset[getLayoutPosition()].getMedia());
        }
    }

    @Override
    public MRLAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                    int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mrl_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final HistoryItem item = mDataset[position];
        holder.uriTv.setText(item.getMrl());
        holder.titleTv.setText(item.getTitle());
    }

    public void setList(HistoryItem[] list){
        mDataset = list;
        notifyDataSetChanged();
    }

    public HistoryItem getItem(int position) {
        if (position >= getItemCount() || position < 0)
            return null;
        return mDataset[position];
    }

    @Override
    public int getItemCount() {
        return mDataset.length;
    }

    public boolean isEmpty(){
        return getItemCount() == 0;
    }
}
