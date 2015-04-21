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
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;

import java.util.ArrayList;

public class AlbumAdapter extends ArrayAdapter<MediaWrapper> {

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
            v = inflater.inflate(R.layout.audio_browser_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.subtitle = (TextView) v.findViewById(R.id.subtitle);
            holder.more = (ImageView) v.findViewById(R.id.item_more);
            holder.footer = v.findViewById(R.id.footer);

            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        holder.title.setText(mw.getTitle());
        holder.subtitle.setText(mw.getArtist());
        holder.footer.setVisibility(position == mMediaList.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        if (mContextPopupMenuListener != null)
            holder.more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mContextPopupMenuListener != null)
                        mContextPopupMenuListener.onPopupMenu(v, position);
                }
            });
        else
            holder.more.setVisibility(View.GONE);
        v.findViewById(R.id.cover).setVisibility(View.GONE);
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

    static class ViewHolder {
        TextView title;
        TextView subtitle;
        ImageView more;
        View footer;
    }

    public interface ContextPopupMenuListener {
        void onPopupMenu(View anchor, final int position);
    }

    void setContextPopupMenuListener(ContextPopupMenuListener l) {
        mContextPopupMenuListener = l;
    }
}
