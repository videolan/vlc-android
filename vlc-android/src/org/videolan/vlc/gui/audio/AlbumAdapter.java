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
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.interfaces.IAudioClickHandler;

import java.util.ArrayList;

public class AlbumAdapter extends ArrayAdapter<MediaWrapper> implements IAudioClickHandler{

    private ArrayList<MediaWrapper> mMediaList;

    private ContextPopupMenuListener mContextPopupMenuListener;

    public AlbumAdapter(Context context, ArrayList<MediaWrapper> tracks) {
        super(context, 0);
        mMediaList = tracks;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        MediaWrapper mw = mMediaList.get(position);
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            holder = new ViewHolder();
            holder.binding = DataBindingUtil.inflate(inflater, R.layout.audio_browser_item, parent, false);
            v = holder.binding.getRoot();

            v.setTag(R.layout.audio_browser_item, holder);
        } else
            holder = (ViewHolder) v.getTag(R.layout.audio_browser_item);

        holder.position = position;
        holder.binding.setMedia(mw);
        holder.binding.setFooter(position != mMediaList.size() - 1);
        holder.binding.setClickable(mContextPopupMenuListener != null);
        holder.binding.setHandler(this);
        holder.binding.executePendingBindings();
        return v;
    }

    @Override
    public int getCount() {
        return mMediaList == null ? 0 : mMediaList.size();
    }

    @Nullable
    public String getLocation(int position) {
        if (position >= 0 && position < mMediaList.size())
            return mMediaList.get(position).getLocation();
        else
            return null;
    }

    public void addAll(ArrayList<MediaWrapper> tracks){
        mMediaList = tracks;
        notifyDataSetChanged();
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null)
            super.unregisterDataSetObserver(observer);
    }

    @Override
    public void onMoreClick(View v) {
        if (mContextPopupMenuListener != null)
                mContextPopupMenuListener.onPopupMenu(v, ((ViewHolder) ((LinearLayout)v.getParent().getParent()).getTag(R.layout.audio_browser_item)).position);
    }

    static class ViewHolder {
        AudioBrowserItemBinding binding;
        int position;
    }

    public interface ContextPopupMenuListener {
        void onPopupMenu(View anchor, final int position);
    }

    void setContextPopupMenuListener(ContextPopupMenuListener l) {
        mContextPopupMenuListener = l;
    }
}
