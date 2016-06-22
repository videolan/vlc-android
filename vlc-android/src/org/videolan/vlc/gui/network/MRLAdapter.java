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
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;

import java.util.ArrayList;

public class MRLAdapter extends RecyclerView.Adapter<MRLAdapter.ViewHolder> {
    private ArrayList<String> mDataset;
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView uriTv;
        public ImageView deleteButton;

        public ViewHolder(View v) {
            super(v);
            uriTv = (TextView) v.findViewById(R.id.mrl_item_uri);
            deleteButton = (ImageView) v.findViewById(R.id.mrl_item_delete);
        }
    }

    public MRLAdapter(ArrayList<String> myDataset) {
        mDataset = myDataset;
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
        holder.uriTv.setText(mDataset.get(position));
        holder.uriTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiTools.setKeyboardVisibility(holder.itemView, false);
                MediaUtils.openStream(v.getContext(), getItem(holder.getAdapterPosition()));
            }
        });
        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiTools.setKeyboardVisibility(holder.itemView, false);
                final int currentPosition = holder.getAdapterPosition();
                if (currentPosition > -1) {
                    final String mrl = getItem(currentPosition);
                    if (mrl == null)
                        return;
                    mDataset.remove(currentPosition);
                    notifyItemRemoved(currentPosition);
                    UiTools.snackerWithCancel(holder.itemView,
                            holder.itemView.getContext().getString(R.string.file_deleted),
                            new Runnable() {
                                @Override
                                public void run() {
                                    MediaDatabase.getInstance().deleteMrlUri(mrl);
                                }
                            }, new Runnable() {
                                @Override
                                public void run() {
                                    mDataset.add(currentPosition, mrl);
                                    notifyItemInserted(currentPosition);
                                }
                            });
                }
            }
        });
    }

    public void setList(ArrayList<String> list){
        mDataset = list;
        notifyDataSetChanged();
    }

    public String getItem(int position) {
        if (position >= getItemCount() || position < 0)
            return null;
        return mDataset.get(position);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public boolean isEmpty(){
        return getItemCount() == 0;
    }
}
