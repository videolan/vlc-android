/*****************************************************************************
 * AudioSongsListAdapter.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio;

import java.util.ArrayList;
import java.util.List;

import org.videolan.libvlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.widget.AudioPlaylistItemViewGroup;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AudioPlaylistAdapter extends ArrayAdapter<Media> {

    private ArrayList<Media> mMediaList;
    private int mCurrentIndex;
    private Context mContext;

    public AudioPlaylistAdapter(Context context) {
        super(context, 0);
        mContext = context;
        mMediaList = new ArrayList<Media>();
        mCurrentIndex = -1;
    }

    @Override
    public void add(Media m) {
        mMediaList.add(m);
        super.add(m);
    }

    @Override
    public void remove(Media m) {
        mMediaList.remove(m);
        super.remove(m);
    }

    @Override
    public void clear() {
        mMediaList.clear();
        super.clear();
    }

    public void setCurrentIndex(int currentIndex) {
        mCurrentIndex = currentIndex;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_playlist_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.artist = (TextView) v.findViewById(R.id.artist);
            holder.moveButton = (ImageButton) v.findViewById(R.id.move);
            holder.expansion = (LinearLayout)v.findViewById(R.id.item_expansion);
            holder.layoutItem = (LinearLayout)v.findViewById(R.id.layout_item);
            holder.layoutFooter = (View)v.findViewById(R.id.layout_footer);
            holder.itemGroup = (AudioPlaylistItemViewGroup)v.findViewById(R.id.playlist_item);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        holder.expansion.setVisibility(LinearLayout.GONE);
        holder.layoutItem.setVisibility(LinearLayout.VISIBLE);
        holder.layoutFooter.setVisibility(LinearLayout.VISIBLE);
        holder.itemGroup.scrollTo(1);

        Media media = getItem(position);
        final String title = media.getTitle();
        final String artist = media.getSubtitle();
        final int pos = position;
        final View itemView = v;

        holder.title.setText(title);
        ColorStateList titleColor = v.getResources().getColorStateList(mCurrentIndex == position
                ? Util.getResourceFromAttribute(mContext, R.attr.list_title_last)
                : Util.getResourceFromAttribute(mContext, R.attr.list_title));
        holder.title.setTextColor(titleColor);
        holder.artist.setText(artist);
        holder.position = position;

        final AudioPlaylistView playlistView = (AudioPlaylistView)parent;

        holder.moveButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    playlistView.startDrag(pos, title, artist);
                    return true;
                }
                else
                    return false;
            }
        });
        holder.itemGroup.setOnItemSlidedListener(
                new AudioPlaylistItemViewGroup.OnItemSlidedListener() {
            @Override
            public void onItemSlided() {
                playlistView.removeItem(pos);
            }
        });
        holder.layoutItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistView.performItemClick(itemView, pos, 0);
            }
        });
        holder.layoutItem.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                playlistView.performItemLongClick(itemView, pos, 0);
                return true;
            }
        });

        return v;
    }

    public List<String> getLocation(int position) {
        List<String> locations = new ArrayList<String>();
        if (position >= 0 && position < mMediaList.size())
            locations.add(mMediaList.get(position).getLocation());
        return locations;
    }

    public List<String> getLocations() {
        List<String> locations = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            locations.add(mMediaList.get(i).getLocation());
        }
        return locations;
    }

    static class ViewHolder {
        int position;
        TextView title;
        TextView artist;
        ImageButton moveButton;
        LinearLayout expansion;
        LinearLayout layoutItem;
        View layoutFooter;
        AudioPlaylistItemViewGroup itemGroup;
    }
}
