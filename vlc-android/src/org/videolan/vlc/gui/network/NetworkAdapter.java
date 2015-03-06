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

public class NetworkAdapter extends  RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "VLC/NetworkAdapter";

    private static final int TYPE_MEDIA = 0;
    private static final int TYPE_SEPARATOR = 1;

    ArrayList<Object> mMediaList = new ArrayList<Object>();
    NetworkFragment fragment;

    public NetworkAdapter(NetworkFragment fragment){
        this.fragment = fragment;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh;
        View v;
        if (viewType == TYPE_MEDIA) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.directory_view_item, parent, false);
            vh = new MediaViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.browser_item_separator, parent, false);
            vh = new SeparatorViewHolder(v);
        }
        return vh;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MediaViewHolder) {
            MediaViewHolder vh = (MediaViewHolder) holder;
            MediaWrapper media = (MediaWrapper) getItem(position);
            vh.title.setText(media.getTitle());
            vh.text.setVisibility(View.GONE);
            vh.icon.setImageResource(getIconResId(media));
            vh.more.setVisibility(View.GONE);
            vh.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MediaWrapper mw = (MediaWrapper) getItem(holder.getPosition());
                    if (mw.getType() == MediaWrapper.TYPE_DIR)
                        fragment.browse(mw);
                    else
                        Util.openMedia(v.getContext(), mw);
                }
            });
        } else {
            SeparatorViewHolder vh = (SeparatorViewHolder) holder;
            vh.title.setText(getItem(position).toString());
        }
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView text;
        public ImageView icon;
        public ImageView more;

        public MediaViewHolder(View v) {
            super(v);
            title = (TextView) v.findViewById(R.id.title);
            text = (TextView) v.findViewById(R.id.text);
            icon = (ImageView) v.findViewById(R.id.dvi_icon);
            more = (ImageView) v.findViewById(R.id.item_more);
        }
    }

    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        public TextView title;

        public SeparatorViewHolder(View v) {
            super(v);
            title = (TextView) v.findViewById(R.id.separator_title);
        }
    }

    public void clear(){
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty(){
        return mMediaList.isEmpty();
    }

    public void addItem(Media media, boolean root, boolean first){
        MediaWrapper mediaWrapper = new MediaWrapper(media);
        addItem(mediaWrapper, root, first);

    }

    public void addItem(Object item, boolean root, boolean first){
        int position = first ? 0 : mMediaList.size();
        if (item instanceof MediaWrapper && ((MediaWrapper)item).getTitle().startsWith("."))
            return;
        else if (item instanceof Media)
            item = new MediaWrapper((Media) item);

        mMediaList.add(position, item);
        if (root)
            notifyItemInserted(position);
    }

    public void removeItem(int position){
        mMediaList.remove(position);
        notifyItemRemoved(position);
    }

    public Object getItem(int position){
        return mMediaList.get(position);
    }

    public int getItemViewType(int position){
        if (getItem(position) instanceof  MediaWrapper)
            return TYPE_MEDIA;
        else
            return TYPE_SEPARATOR;
    }

    public void sortList(){
        ArrayList<MediaWrapper> files = new ArrayList<MediaWrapper>(), dirs = new ArrayList<MediaWrapper>();
        for (Object item : mMediaList){
            if (item instanceof MediaWrapper) {
                MediaWrapper media = (MediaWrapper) item;
                if (media.getType() == MediaWrapper.TYPE_DIR)
                    dirs.add(media);
                else
                    files.add(media);
            }
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
                return R.drawable.ic_browser_audio_normal;
            case MediaWrapper.TYPE_DIR:
                return R.drawable.ic_menu_network;
            case MediaWrapper.TYPE_VIDEO:
                return R.drawable.ic_browser_video_normal;
            case MediaWrapper.TYPE_SUBTITLE:
                return R.drawable.ic_browser_subtitle_normal;
            default:
                return R.drawable.ic_browser_unknown_normal;
        }
    }
}
