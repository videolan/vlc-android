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
import android.support.annotation.MainThread;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.PlaylistItemBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> implements SwipeDragHelperAdapter, Filterable{

    private ItemFilter mFilter = new ItemFilter();

    public interface IPlayer {
        void onPopupMenu(View view, int position);
        void updateList();
        void onSelectionSet(int position);
    }
    private static final String TAG = "VLC/PlaylistAdapter";
    PlaybackService mService = null;
    IPlayer mAudioPlayer;

    private ArrayList<MediaWrapper> mDataSet = new ArrayList();
    private ArrayList<MediaWrapper> mOriginalDataSet = new ArrayList<MediaWrapper>();
    private int mCurrentIndex = 0;

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

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    public String getLocation(int position) {
        MediaWrapper item = getItem(position);
        return item == null ? "" : item.getLocation();
    }

    public List<MediaWrapper> getMedias() {
        return mDataSet;
    }

    public void addAll(List<MediaWrapper> playList) {
        mDataSet.addAll(playList);
        mOriginalDataSet.addAll(playList);
    }

    public void add(MediaWrapper mw) {
        mDataSet.add(mw);
        mOriginalDataSet.add(mw);
    }

    @MainThread
    public void remove(int position) {
        if (mService == null)
            return;
        mDataSet.remove(position);
        mOriginalDataSet.remove(position);
        mService.remove(position);
        notifyItemRemoved(position);
    }

    public void clear(){
        mDataSet.clear();
        mOriginalDataSet.clear();
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public void setCurrentIndex(int position) {
        if (position == mCurrentIndex || position < 0 || position >= mDataSet.size())
            return;
        int former = mCurrentIndex;
        mCurrentIndex = position;
        notifyItemChanged(former);
        notifyItemChanged(position);
        mAudioPlayer.onSelectionSet(position);
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(mDataSet, fromPosition, toPosition);
        Collections.swap(mOriginalDataSet, fromPosition, toPosition);
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
                    mDataSet.add(position, media);
                    mOriginalDataSet.add(position, media);
                    notifyItemInserted(position);
                    mService.insertItem(position, media);
                }
            };
            UiTools.snackerWithCancel(v, message, null, cancelAction);
        } else if (mAudioPlayer instanceof Context){
            Toast.makeText((Context) mAudioPlayer, message, Toast.LENGTH_SHORT).show();
        }
        remove(position);
    }

    public void setService(PlaybackService service) {
        mService = service;
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        PlaylistItemBinding binding;

        public ViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
            binding.setHolder(this);
        }
        public void onClick(View v){
            int position = getMediaPosition();
            if (mService != null)
                mService.playIndex(position);
            if (mDataSet.size() != mOriginalDataSet.size())
                restoreList();
        }
        public void onMoreClick(View v){
            mAudioPlayer.onPopupMenu(v, getLayoutPosition());
        }

        private int getMediaPosition() {
            if (mDataSet.size() == mOriginalDataSet.size())
                return getLayoutPosition();
            else {
                MediaWrapper mw, media = mDataSet.get(getAdapterPosition());
                for (int i = 0 ; i < mOriginalDataSet.size() ; ++i) {
                    mw = mOriginalDataSet.get(i);
                    if (mw.equals(media))
                        return i;
                }
                return 0;
            }
        }
    }

    public void restoreList() {
        if (mDataSet.size() == mOriginalDataSet.size())
            return;
        mDataSet = new ArrayList<>(mOriginalDataSet.size());
        mDataSet.addAll(mOriginalDataSet);
        notifyDataSetChanged();
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

    private class ItemFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            final String[] queryStrings = charSequence.toString().trim().toLowerCase().split(" ");
            FilterResults results = new FilterResults();
            ArrayList<MediaWrapper> list = new ArrayList<>(mOriginalDataSet.size());
            String title, location, artist, album, albumArtist, genre;
            MediaWrapper media;
            mediaLoop:
            for (int i = 0 ; i < mOriginalDataSet.size() ; ++i) {
                media = mOriginalDataSet.get(i);
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
            mDataSet = (ArrayList<MediaWrapper>) filterResults.values;
            notifyDataSetChanged();
        }
    }
}
