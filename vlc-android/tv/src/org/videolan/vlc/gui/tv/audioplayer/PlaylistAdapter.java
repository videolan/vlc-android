/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors, VideoLAN and VideoLabs
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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaLibrary;

import java.util.ArrayList;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
	public static final String TAG = "VLC/PlaylistAdapter";

    private ArrayList<String> mDataset;
	private static MediaLibrary sMediaLibrary = MediaLibrary.getInstance();
    private int mSelectedItem = -1;

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
                               .inflate(android.R.layout.simple_list_item_activated_2, parent, false);

        v.setClickable(true);
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Media media = sMediaLibrary.getMediaItem(mDataset.get(position));
        holder.mTitleTv.setText(media.getTitle());
        holder.mArtistTv.setText(media.getArtist());
        holder.itemView.setActivated(position == mSelectedItem);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void setSelection(int pos){
        if (pos == mSelectedItem)
            return;
        int previous = mSelectedItem;
        mSelectedItem = pos;
        notifyItemChanged(previous);
        notifyItemChanged(mSelectedItem);
    }
}