/**
 * **************************************************************************
 * BaseBrowserAdapter.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.browser;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BaseBrowserAdapter extends  RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "VLC/BaseBrowserAdapter";

    private static final int TYPE_MEDIA = 0;
    private static final int TYPE_SEPARATOR = 1;
    private static final int TYPE_STORAGE = 2;

    protected int FOLDER_RES_ID = R.drawable.ic_menu_folder;

    ArrayList<Object> mMediaList = new ArrayList<Object>();
    BaseBrowserFragment fragment;
    MediaDatabase mDbManager;
    LinkedList<String> mMediaDirsLocation;
    List<String> mCustomDirsLocation;

    public BaseBrowserAdapter(BaseBrowserFragment fragment){
        this.fragment = fragment;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh;
        View v;
        if (viewType == TYPE_MEDIA || viewType == TYPE_STORAGE) {
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
        int viewType = getItemViewType(position);
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder(holder, position);
        } else if (viewType == TYPE_STORAGE) {
            onBindStorageViewHolder(holder, position);
        } else {
            SeparatorViewHolder vh = (SeparatorViewHolder) holder;
            vh.title.setText(getItem(position).toString());
        }
    }

    private void onBindMediaViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final MediaWrapper media = (MediaWrapper) getItem(position);
        boolean hasContextMenu = (media.getType() == MediaWrapper.TYPE_AUDIO ||
                media.getType() == MediaWrapper.TYPE_VIDEO ||
                (media.getType() == MediaWrapper.TYPE_DIR && Util.canWrite(media.getLocation())));
        vh.checkBox.setVisibility(View.GONE);
        vh.title.setText(media.getTitle());
        if (!TextUtils.isEmpty(media.getDescription())) {
            vh.text.setVisibility(View.VISIBLE);
            vh.text.setText(media.getDescription());
        } else
            vh.text.setVisibility(View.INVISIBLE);
        vh.icon.setImageResource(getIconResId(media));
        vh.more.setVisibility(hasContextMenu ? View.VISIBLE : View.GONE);
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaWrapper mw = (MediaWrapper) getItem(holder.getAdapterPosition());
                if (mw.getType() == MediaWrapper.TYPE_DIR)
                    fragment.browse(mw, holder.getAdapterPosition());
                else if (mw.getType() == MediaWrapper.TYPE_VIDEO)
                    Util.openMedia(v.getContext(), mw);
                else {
                    int position = 0;
                    LinkedList<MediaWrapper> mediaLocations = new LinkedList<MediaWrapper>();
                    MediaWrapper mediaItem;
                    for (Object item : mMediaList)
                        if (item instanceof MediaWrapper) {
                            mediaItem = (MediaWrapper) item;
                            if (mediaItem.getType() == MediaWrapper.TYPE_VIDEO || mediaItem.getType() == MediaWrapper.TYPE_AUDIO) {
                                mediaLocations.add(mediaItem);
                                if (mediaItem.equals(mw))
                                    position = mediaLocations.size() - 1;
                            }
                        }
                    Util.openList(v.getContext(), mediaLocations, position);
                }
            }
        });
        if (hasContextMenu) {
            vh.more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fragment.onPopupMenu(vh.more, holder.getAdapterPosition());
                }
            });
            vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    fragment.mRecyclerView.openContextMenu(holder.getAdapterPosition());
                    return true;
                }
            });
        }
    }

    private void onBindStorageViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final Storage storage = (Storage) getItem(position);
        boolean isPublicStorage = TextUtils.equals(storage.getPath(), AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
        boolean hasContextMenu = !isPublicStorage && mCustomDirsLocation.contains(storage.getPath());
        vh.title.setText(isPublicStorage ? holder.itemView.getContext().getString(R.string.internal_memory) : storage.getName());
        vh.icon.setVisibility(View.GONE);
        vh.checkBox.setVisibility(View.VISIBLE);
        vh.more.setVisibility(hasContextMenu ? View.VISIBLE : View.GONE);
        String description = storage.getDescription();
        if (!TextUtils.isEmpty(description)) {
            vh.text.setVisibility(View.VISIBLE);
            vh.text.setText(description);
        } else
            vh.text.setVisibility(View.INVISIBLE);

        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaWrapper mw = new MediaWrapper(((Storage)getItem(vh.getAdapterPosition())).getPath());
                mw.setType(MediaWrapper.TYPE_DIR);
                fragment.browse(mw, holder.getAdapterPosition());
            }
        });
        vh.checkBox.setChecked(isPublicStorage ||
                mMediaDirsLocation == null || mMediaDirsLocation.isEmpty() ||
                mMediaDirsLocation.contains(storage.getPath()));
        if (!isPublicStorage) {
            vh.checkBox.setEnabled(true);
            vh.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    String path = ((Storage)getItem(vh.getAdapterPosition())).getPath();
                    updateMediaDirs();
                    if (isChecked)
                        mDbManager.addDir(path);
                    else {
                        if (mMediaDirsLocation == null || mMediaDirsLocation.isEmpty()){
                            String storagePath;
                            for (Object storage : mMediaList){
                                storagePath = ((Storage)storage).getPath();
                                if (!TextUtils.equals(storagePath, path))
                                    mDbManager.addDir(storagePath);
                            }
                        } else
                            mDbManager.removeDir(path);
                    }
                }
            });
            if (hasContextMenu) {
                vh.more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        fragment.onPopupMenu(vh.more, holder.getAdapterPosition());
                    }
                });
                vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        fragment.mRecyclerView.openContextMenu(holder.getAdapterPosition());
                        return true;
                    }
                });
            }
        } else
            vh.checkBox.setEnabled(false);
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public class MediaViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public CheckBox checkBox;
        public TextView text;
        public ImageView icon;
        public ImageView more;

        public MediaViewHolder(View v) {
            super(v);
            title = (TextView) v.findViewById(R.id.title);
            checkBox = (CheckBox) v.findViewById(R.id.browser_checkbox);
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

    public static class Storage {
        String path;
        String name;
        String description;

        public Storage(String location){
            path = location;
            name = Strings.getName(path);
        }

        public String getName() {
            return name;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public String getPath() {
            return path;
        }
    }

    public void clear(){
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty(){
        return mMediaList.isEmpty();
    }

    public void addItem(Media media, boolean notify, boolean top){
        MediaWrapper mediaWrapper = new MediaWrapper(media);
        addItem(mediaWrapper, notify, top);

    }

    public void addItem(Object item, boolean notify, boolean top){
        int position = top ? 0 : mMediaList.size();
        if (item instanceof MediaWrapper && ((MediaWrapper)item).getTitle().startsWith("."))
            return;
        else if (item instanceof Media)
            item = new MediaWrapper((Media) item);

        mMediaList.add(position, item);
        if (notify)
            notifyItemInserted(position);
    }

    public void setDescription(int position, String description){
        Object item = getItem(position);
        if (item instanceof MediaWrapper)
            ((MediaWrapper) item).setDescription(description);
        else if (item instanceof Storage)
            ((Storage) item).setDescription(description);
        else
            return;
        notifyItemChanged(position);
    }

    public void updateMediaDirs(){
        if (mDbManager == null)
            mDbManager = MediaDatabase.getInstance();
        if (mMediaDirsLocation == null)
            mMediaDirsLocation = new LinkedList<String>();
        else
            mMediaDirsLocation.clear();
        List<File> mediaDirs = mDbManager.getMediaDirs();
        for (File dir : mediaDirs){
            mMediaDirsLocation.add(dir.getPath());
        }
        mCustomDirsLocation = Arrays.asList(CustomDirectories.getCustomDirectories());
    }

    public void addAll(ArrayList<MediaWrapper> mediaList){
        mMediaList.clear();
        for (MediaWrapper mw : mediaList)
            mMediaList.add(mw);
    }

    public void removeItem(int position, boolean notify){
        mMediaList.remove(position);
        if (notify) {
            notifyItemRemoved(position);
        }
    }

    public Object getItem(int position){
        return mMediaList.get(position);
    }

    public int getItemViewType(int position){
        if (getItem(position) instanceof  MediaWrapper)
            return TYPE_MEDIA;
        else if (getItem(position) instanceof Storage)
            return TYPE_STORAGE;
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
        if (dirs.isEmpty() && files.isEmpty())
            return;
        mMediaList.clear();
        if (!dirs.isEmpty()) {
            Collections.sort(dirs, MediaComparators.byName);
            mMediaList.addAll(dirs);
        }
        if (!files.isEmpty()) {
            Collections.sort(files, MediaComparators.byName);
            mMediaList.addAll(files);
        }
        notifyDataSetChanged();
    }

    private int getIconResId(MediaWrapper media) {
        switch (media.getType()){
            case MediaWrapper.TYPE_AUDIO:
                return R.drawable.ic_browser_audio_normal;
            case MediaWrapper.TYPE_DIR:
                return FOLDER_RES_ID;
            case MediaWrapper.TYPE_VIDEO:
                return R.drawable.ic_browser_video_normal;
            case MediaWrapper.TYPE_SUBTITLE:
                return R.drawable.ic_browser_subtitle_normal;
            default:
                return R.drawable.ic_browser_unknown_normal;
        }
    }
}
