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
import android.widget.Filter;
import android.widget.Filterable;
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

import java.util.ArrayList;
import java.util.Collections;

public class PlaylistAdapter extends DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder> implements SwipeDragHelperAdapter, Filterable {

    private static final String TAG = "VLC/PlaylistAdapter";

    private ItemFilter mFilter = new ItemFilter();
    private PlaybackService mService = null;
    private IPlayer mAudioPlayer;

    private ArrayList<MediaWrapper> mOriginalDataSet;
    private int mCurrentIndex = 0;

    public interface IPlayer {
        void onPopupMenu(View view, int position);
        void updateList();
        void onSelectionSet(int position);
    }

    public PlaylistAdapter(IPlayer audioPlayer) {
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
        final Context ctx = holder.itemView.getContext();
        final MediaWrapper media = getItem(position);
        holder.binding.setMedia(media);
        holder.binding.setSubTitle(MediaUtils.getMediaSubtitle(media));
        holder.binding.setTitleColor(mOriginalDataSet == null && mCurrentIndex == position
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

    @Override
    public Filter getFilter() {
        return mFilter;
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
        mAudioPlayer.onSelectionSet(position);
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
        if (mAudioPlayer instanceof Fragment){
            View v = ((Fragment) mAudioPlayer).getView();
            Runnable cancelAction = new Runnable() {
                @Override
                public void run() {
                    mService.insertItem(position, media);
                }
            };
            UiTools.snackerWithCancel(v, message, null, cancelAction);
        } else if (mAudioPlayer instanceof Context){
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
            int position = getMediaPosition(media);
            if (mService != null)
                mService.playIndex(position);
            if (mOriginalDataSet != null)
                restoreList();
        }
        public void onMoreClick(View v){
            mAudioPlayer.onPopupMenu(v, getLayoutPosition());
        }

        private int getMediaPosition(MediaWrapper media) {
            if (mOriginalDataSet == null)
                return getLayoutPosition();
            else {
                MediaWrapper mw;
                for (int i = 0 ; i < mOriginalDataSet.size() ; ++i) {
                    mw = mOriginalDataSet.get(i);
                    if (mw.equals(media))
                        return i;
                }
                return 0;
            }
        }
    }

    @MainThread
    public void restoreList() {
        if (mOriginalDataSet != null) {
            update(new ArrayList<>(mOriginalDataSet));
            mOriginalDataSet = null;
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
                    PlaybackService service = getOwner().mService;
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

    private class ItemFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            if (mOriginalDataSet == null)
                mOriginalDataSet = new ArrayList<>(getDataset());
            final String[] queryStrings = charSequence.toString().trim().toLowerCase().split(" ");
            FilterResults results = new FilterResults();
            ArrayList<MediaWrapper> list = new ArrayList<>();
            String title, location, artist, album, albumArtist, genre;
            mediaLoop:
            for (MediaWrapper media : mOriginalDataSet) {
                title = MediaUtils.getMediaTitle(media);
                location = media.getLocation();
                artist = MediaUtils.getMediaArtist(VLCApplication.getAppContext(), media).toLowerCase();
                albumArtist = MediaUtils.getMediaAlbumArtist(VLCApplication.getAppContext(), media).toLowerCase();
                album = MediaUtils.getMediaAlbum(VLCApplication.getAppContext(), media).toLowerCase();
                genre = MediaUtils.getMediaGenre(VLCApplication.getAppContext(), media).toLowerCase();
                for (String queryString : queryStrings) {
                    if (queryString.length() < 2)
                        continue;
                    if (title != null && title.toLowerCase().contains(queryString) ||
                            location != null && location.toLowerCase().contains(queryString) ||
                            artist.contains(queryString) ||
                            albumArtist.contains(queryString) ||
                            album.contains(queryString) ||
                            genre.contains(queryString)) {
                        list.add(media);
                        continue mediaLoop; //avoid duplicates in search results, and skip useless processing
                    }
                }
            }
            results.values = list;
            results.count = list.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update((ArrayList<MediaWrapper>) filterResults.values);
        }
    }

    @NotNull
    @Override
    protected DiffCallback<MediaWrapper> createCB() {
        return new MediaItemDiffCallback();
    }
}
