/*
 * *************************************************************************
 *  AlbumAdapter.java
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
import android.database.DataSetObserver;
import android.databinding.DataBindingUtil;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.interfaces.IAudioClickHandler;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;
import java.util.Collections;

public class AlbumAdapter extends BaseAdapter implements IAudioClickHandler{

    private Context mContext;
    private ArrayList<MediaWrapper> mMediaList;

    private ContextPopupMenuListener mContextPopupMenuListener;

    public AlbumAdapter(Context context, ArrayList<MediaWrapper> tracks) {
        mContext = context;
        addAll(tracks);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        MediaWrapper mw = mMediaList.get(position);
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            holder = new ViewHolder();
            holder.binding = DataBindingUtil.inflate(inflater, R.layout.audio_browser_item, parent, false);
            v = holder.binding.getRoot();

            v.setTag(R.layout.audio_browser_item, holder);
        } else
            holder = (ViewHolder) v.getTag(R.layout.audio_browser_item);

        holder.binding.setPosition(position);
        holder.binding.setMedia(mw);
        holder.binding.setHasFooter(position != mMediaList.size() - 1);
        holder.binding.setClickable(mContextPopupMenuListener != null);
        holder.binding.setHandler(this);
        holder.binding.executePendingBindings();
        return v;
    }

    @MainThread
    public void addMedia(int position, MediaWrapper media) {
        mMediaList.add(position, media);
        notifyDataSetChanged();
    }

    @MainThread void removeMedia(int position) {
        mMediaList.remove(position);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mMediaList == null ? 0 : mMediaList.size();
    }

    public MediaWrapper getItem(int position) {
        return mMediaList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Nullable
    public String getLocation(int position) {
        if (position >= 0 && position < mMediaList.size())
            return mMediaList.get(position).getLocation();
        else
            return null;
    }

    public void addAll(ArrayList<MediaWrapper> tracks){
        if (tracks != null) {
            Collections.sort(tracks, MediaComparators.byTrackNumber);
            mMediaList = new ArrayList<>(tracks);
            notifyDataSetChanged();
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null)
            super.unregisterDataSetObserver(observer);
    }

    @Override
    public void onMoreClick(View v) {
        if (mContextPopupMenuListener != null)
                mContextPopupMenuListener.onPopupMenu(v, ((Integer) v.getTag()).intValue());
    }

    public void clear() {
        mMediaList.clear();
    }

    static class ViewHolder {
        AudioBrowserItemBinding binding;
    }

    public interface ContextPopupMenuListener {
        void onPopupMenu(View anchor, final int position);
    }

    void setContextPopupMenuListener(ContextPopupMenuListener l) {
        mContextPopupMenuListener = l;
    }
}
