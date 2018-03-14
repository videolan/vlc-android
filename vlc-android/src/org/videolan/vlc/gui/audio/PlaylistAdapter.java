/*
 * *************************************************************************
 *  PlaylistAdapter.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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
import android.support.annotation.MainThread;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.PlaylistItemBinding;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.MediaItemDiffCallback;
import org.videolan.vlc.util.WeakHandler;

import java.util.Collections;

public class PlaylistAdapter extends DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder> implements SwipeDragHelperAdapter {

    private static final String TAG = "VLC/PlaylistAdapter";

    private PlaybackService mService = null;
    private IPlayer mPlayer;

    private int mCurrentIndex = 0;

    public interface IPlayer {
        void onPopupMenu(View view, int position, MediaWrapper item);
        void onSelectionSet(int position);
        void playItem(int position, MediaWrapper item);
    }

    public PlaylistAdapter(IPlayer audioPlayer) {
        mPlayer = audioPlayer;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final Context ctx = holder.itemView.getContext();
        final MediaWrapper media = getItem(position);
        holder.binding.setMedia(media);
        holder.binding.setSubTitle(MediaUtils.getMediaSubtitle(media));
        holder.binding.setTitleColor(mCurrentIndex == position
                ? UiTools.getColorFromAttribute(ctx, R.attr.list_title_last)
                : UiTools.getColorFromAttribute(ctx, R.attr.list_title));
        holder.binding.executePendingBindings();
    }

    @Override
    public int getItemCount() {
        return getDataset().size();
    }

    @MainThread
    public MediaWrapper getItem(int position) {
        if (position >= 0 && position < getItemCount())
            return getDataset().get(position);
        else
            return null;
    }

    public String getLocation(int position) {
        MediaWrapper item = getItem(position);
        return item == null ? "" : item.getLocation();
    }

    @Override
    protected void onUpdateFinished() {
        if (mService != null)
            setCurrentIndex(mService.getCurrentMediaPosition());
    }

    @MainThread
    public void remove(int position) {
        if (mService == null) return;
        if (position == mCurrentIndex) mCurrentIndex = -1;
        mService.remove(position);
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public void setCurrentIndex(int position) {
        if (position == mCurrentIndex || position < 0 || position >= getItemCount())
            return;
        int former = mCurrentIndex;
        mCurrentIndex = position;
        notifyItemChanged(former);
        notifyItemChanged(position);
        mPlayer.onSelectionSet(position);
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(getDataset(), fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        mHandler.obtainMessage(PlaylistHandler.ACTION_MOVE, fromPosition, toPosition).sendToTarget();
    }

    @Override
    public void onItemDismiss(final int position) {
        final MediaWrapper media = getItem(position);
        String message = String.format(VLCApplication.getAppResources().getString(R.string.remove_playlist_item), media.getTitle());
        if (mPlayer instanceof Fragment){
            View v = ((Fragment) mPlayer).getView();
            Runnable cancelAction = new Runnable() {
                @Override
                public void run() {
                    mService.insertItem(position, media);
                }
            };
            UiTools.snackerWithCancel(v, message, null, cancelAction);
        } else if (mPlayer instanceof Context){
            Toast.makeText(VLCApplication.getAppContext(), message, Toast.LENGTH_SHORT).show();
        }
        remove(position);
    }

    public void setService(PlaybackService service) {
        mService = service;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        PlaylistItemBinding binding;

        public ViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
            binding.setHolder(this);
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener(new View.OnContextClickListener() {
                @Override
                public boolean onContextClick(View v) {
                    onMoreClick(v);
                    return true;
                }
            });
        }
        public void onClick(View v, MediaWrapper media){
            int position = getLayoutPosition(); //getMediaPosition(media);
            mPlayer.playItem(position, media);
        }

        public void onMoreClick(View v) {
            final int position = getLayoutPosition();
            mPlayer.onPopupMenu(v, position, getItem(position));
        }
    }

    private PlaylistHandler mHandler = new PlaylistHandler(this);

    private static class PlaylistHandler extends WeakHandler<PlaylistAdapter>{

        static final int ACTION_MOVE = 0;
        static final int ACTION_MOVED = 1;

        int from = -1, to = -1;

        PlaylistHandler(PlaylistAdapter owner) {
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
                    final PlaybackService service = getOwner().mService;
                    if (from != -1 && to != -1 && service == null)
                        return;
                    if (to > from)
                        ++to;
                    service.moveItem(from, to);
                    from = to = -1;
                    break;
            }
        }
    }

    @NotNull
    @Override
    protected DiffCallback<MediaWrapper> createCB() {
        return new MediaItemDiffCallback();
    }
}
