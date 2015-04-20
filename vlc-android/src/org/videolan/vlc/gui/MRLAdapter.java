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
package org.videolan.vlc.gui;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;

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
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final String uri = mDataset.get(position);
        holder.uriTv.setText(uri);
        holder.uriTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.openStream(v.getContext(), uri);
            }
        });
        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getPosition();
                if (pos > -1) {
                    MediaDatabase.getInstance().deleteMrlUri(mDataset.get(pos));
                    mDataset.remove(pos);
                    notifyItemRemoved(pos);
                }
            }
        });
    }

    public void setList(ArrayList<String> list){
        mDataset = list;
        notifyDataSetChanged();
    }
    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public boolean isEmpty(){
        return getItemCount() == 0;
    }
}
