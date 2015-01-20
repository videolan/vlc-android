/**
 * **************************************************************************
 * NetworkAdapter.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.network;

import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;

public class NetworkAdapter extends  RecyclerView.Adapter<NetworkAdapter.ViewHolder> {
    private static final String TAG = "VLC/NetworkAdapter";

    ArrayList<MediaWrapper> mMediaList = new ArrayList<MediaWrapper>();
    NetworkFragment fragment;

    public NetworkAdapter(NetworkFragment fragment){
        this.fragment = fragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.directory_view_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaWrapper media = getItem(position);
        holder.title.setText(media.getTitle());
        holder.text.setVisibility(View.GONE);
        holder.icon.setImageResource(getIconResId(media));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (media.getType() == MediaWrapper.TYPE_DIR)
                    fragment.browse(media);
                else
                    Util.openMedia(v.getContext(), media);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView text;
        public ImageView icon;

        public ViewHolder(View v) {
            super(v);
            title = (TextView) v.findViewById(R.id.title);
            text = (TextView) v.findViewById(R.id.text);
            icon = (ImageView) v.findViewById(R.id.dvi_icon);
        }
    }

    public void clear(){
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public void addItem(Media media, boolean update){
        MediaWrapper mediaWrapper = new MediaWrapper(media);
        if (mediaWrapper.getTitle().startsWith("."))
            return;
        mMediaList.add(mediaWrapper);
        if (update)
            notifyItemInserted(mMediaList.size()-1);
    }

    public MediaWrapper getItem(int position){
        return mMediaList.get(position);
    }


    public void sortList(){
        ArrayList<MediaWrapper> files = new ArrayList<MediaWrapper>(), dirs = new ArrayList<MediaWrapper>();
        for (MediaWrapper media : mMediaList){
            if (media.getType() == MediaWrapper.TYPE_DIR)
                dirs.add(media);
            else
                files.add(media);
        }
        Collections.sort(dirs, MediaComparators.byName);
        Collections.sort(files, MediaComparators.byName);
        mMediaList.clear();
        mMediaList.addAll(dirs);
        mMediaList.addAll(files);
        notifyDataSetChanged();
    }

    private int getIconResId(MediaWrapper media) {
        switch (media.getType()){
            case MediaWrapper.TYPE_AUDIO:
                return R.drawable.ic_menu_audio;
            case MediaWrapper.TYPE_DIR:
                return R.drawable.ic_menu_folder;
            case MediaWrapper.TYPE_VIDEO:
                return R.drawable.ic_menu_video;
            case MediaWrapper.TYPE_SUBTITLE:
                return R.drawable.ic_subtitle_circle_normal;
            default:
                return R.drawable.ic_cone_o;
        }
    }
}
