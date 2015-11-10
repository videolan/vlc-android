/*
 * *************************************************************************
 *  PlaylistAdapter.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
import android.databinding.DataBindingUtil;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.PlaylistItemBinding;
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> implements SwipeDragHelperAdapter{

    private static final String TAG = "VLC/PlaylistAdapter";
    PlaybackService mService = null;
    AudioPlayer mAudioPlayer;

    private ArrayList<MediaWrapper> mDataSet = new ArrayList<MediaWrapper>();
    private int mCurrentIndex = 0;

    public PlaylistAdapter(AudioPlayer audioPlayer) {
        mAudioPlayer = audioPlayer;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Context ctx = holder.itemView.getContext();
        final MediaWrapper media = getItem(position);
        holder.binding.setPosition(position);
        holder.binding.setHandler(mClickHandler);
        holder.binding.setMedia(media);
        holder.binding.setSubTitle(Util.getMediaSubtitle(ctx, media));
        holder.binding.setTitleColor(mCurrentIndex == position
                ? UiTools.getColorFromAttribute(ctx, R.attr.list_title_last)
                : UiTools.getColorFromAttribute(ctx, R.attr.list_title));
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public MediaWrapper getItem(int position) {
        if (position >= 0 && position < getItemCount())
            return mDataSet.get(position);
        else
            return null;
    }

    public String getLocation(int position) {
        MediaWrapper item = getItem(position);
        return item == null ? "" : item.getLocation();
    }

    public List<MediaWrapper> getMedias() {
        return mDataSet;
    }

    public void add(MediaWrapper mw) {
        mDataSet.add(mw);
    }

    public void clear(){
        mDataSet.clear();
    }

    public void setCurrentIndex(int position) {
        int former = mCurrentIndex;
        mCurrentIndex = position;
        notifyItemChanged(former);
        notifyItemChanged(position);
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(mDataSet, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        mHandler.obtainMessage(PlaylistHandler.ACTION_MOVE, fromPosition, toPosition).sendToTarget();
    }

    @Override
    public void onItemDismiss(int position) {
        if (mService == null)
            return;
        mService.remove(position);
    }

    public void setService(PlaybackService service) {
        mService = service;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        PlaylistItemBinding binding;

        public ViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
        }
    }

    public ClickHandler mClickHandler = new ClickHandler();

    public class ClickHandler {
        public void onClick(View v){
            if (mService != null)
                mService.playIndex(((Integer)v.getTag()).intValue());
        }
        public void onMoreClick(View v){
            mAudioPlayer.onPopupMenu(v, ((Integer)v.getTag()).intValue());
        }
    }

    private PlaylistHandler mHandler = new PlaylistHandler(this);

    private static class PlaylistHandler extends WeakHandler<PlaylistAdapter>{

        public static final int ACTION_MOVE = 0;
        public static final int ACTION_MOVED = 1;

        int from = -1, to = -1;

        public PlaylistHandler(PlaylistAdapter owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case ACTION_MOVE:
                    removeMessages(ACTION_MOVED);
                    if (from == -1)
                        from = msg.arg1;
                    to = msg.arg2;
                    sendEmptyMessageDelayed(ACTION_MOVED, 1000);
                    break;
                case ACTION_MOVED:
                    PlaybackService service = getOwner().mService;
                    if (from != -1 && to != -1 && service == null)
                        return;
                    if (to > from)
                        ++to;
                    service.moveItem(from, to);
                    from = to = -1;
                    getOwner().mAudioPlayer.updateList();
                    break;
            }
        }
    }
}
