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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> implements View.OnClickListener{
    public static final String TAG = "VLC/PlaylistAdapter";

    private AudioPlayerActivity mAudioPlayerActivity;
    private ArrayList<MediaWrapper> mDataset;
    private static MediaLibrary sMediaLibrary = MediaLibrary.getInstance();
    private int mSelectedItem = -1;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTitleTv;
        public TextView mArtistTv;
        public ViewHolder(View v) {
            super(v);
            mTitleTv = (TextView) v.findViewById(android.R.id.text1);
            mArtistTv = (TextView) v.findViewById(android.R.id.text2);
        }
    }

    public PlaylistAdapter(AudioPlayerActivity audioPlayerActivity, ArrayList<MediaWrapper> myDataset) {
        mDataset = myDataset;
        mAudioPlayerActivity = audioPlayerActivity;
    }

    @Override
    public PlaylistAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_simple_list_item, parent, false);

        v.setClickable(true);
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MediaWrapper mediaWrapper = mDataset.get(position);
        holder.mTitleTv.setText(MediaUtils.getMediaTitle(mediaWrapper));
        holder.mArtistTv.setText(MediaUtils.getMediaArtist(holder.itemView.getContext(), mediaWrapper));
        holder.itemView.setActivated(position == mSelectedItem);
        holder.itemView.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public int getmSelectedItem(){
        return mSelectedItem;
    }

    public void setSelection(int pos) {
        if (pos == mSelectedItem)
            return;
        int previous = mSelectedItem;
        mSelectedItem = pos;
        if (previous != -1)
            notifyItemChanged(previous);
        if (pos != -1){
            notifyItemChanged(mSelectedItem);
        }
    }

    public void updateList(ArrayList<MediaWrapper> list){
        mDataset = list;
        notifyDataSetChanged();
    }

    @Override
    public void onClick(View v){
        mAudioPlayerActivity.playSelection();
    }
}