/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors and VideoLAN
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

import java.util.ArrayList;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaLibrary;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
    private ArrayList<String> mDataset;
	private static MediaLibrary sMediaLibrary = MediaLibrary.getInstance();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTitleTv;
        public TextView mArtistTv;
        public ViewHolder(View v) {
            super(v);
            mTitleTv = (TextView) v.findViewById(android.R.id.text1);
            mTitleTv.setTextAppearance(v.getContext(), android.R.style.TextAppearance_DeviceDefault_Small);
            mArtistTv = (TextView) v.findViewById(android.R.id.text2);
            mArtistTv.setTextAppearance(v.getContext(), android.R.style.TextAppearance_DeviceDefault_Small_Inverse);
        }
    }

    public PlaylistAdapter(ArrayList<String> myDataset) {
        mDataset = myDataset;
    }

    @Override
    public PlaylistAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(android.R.layout.simple_list_item_2, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Media media = sMediaLibrary.getMediaItem(mDataset.get(position));
        holder.mTitleTv.setText(media.getTitle());
        holder.mArtistTv.setText(media.getArtist());
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}