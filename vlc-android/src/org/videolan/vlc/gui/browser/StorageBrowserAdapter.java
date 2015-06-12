/*
 * *************************************************************************
 *  StorageBrowserAdapter.java
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

package org.videolan.vlc.gui.browser;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;

public class StorageBrowserAdapter extends BaseBrowserAdapter {

    boolean isRoot;
    public StorageBrowserAdapter(BaseBrowserFragment fragment) {
        super(fragment);
        updateMediaDirs();
        isRoot = fragment.isRootDirectory();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh;
        View v;
        v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.directory_view_item, parent, false);
        vh = new MediaViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final Storage storage = (Storage) getItem(position);
        String storagePath = storage.getUri().getPath();
        boolean hasContextMenu = mCustomDirsLocation.contains(storagePath);
        vh.title.setText(storage.getName());
        vh.icon.setVisibility(View.GONE);
        vh.checkBox.setVisibility(View.VISIBLE);
        vh.more.setVisibility(hasContextMenu ? View.VISIBLE : View.GONE);
        vh.text.setVisibility(View.GONE);

        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaWrapper mw = new MediaWrapper(((Storage) getItem(vh.getAdapterPosition())).getUri());
                mw.setType(MediaWrapper.TYPE_DIR);
                ((StorageBrowserFragment) fragment).browse(mw, holder.getAdapterPosition(), vh.checkBox.isChecked());
            }
        });
        vh.checkBox.setChecked(((StorageBrowserFragment) fragment).mScannedDirectory ||
                (isRoot && (mMediaDirsLocation == null || mMediaDirsLocation.isEmpty())) ||
                mMediaDirsLocation.contains(storagePath));
        vh.checkBox.setEnabled(!((StorageBrowserFragment) fragment).mScannedDirectory);
        vh.checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isChecked = ((CheckBox) v).isChecked();
                String path = ((Storage) getItem(vh.getAdapterPosition())).getUri().getPath();
                if (isChecked)
                    addDir(path);
                else
                    removeDir(path);
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

    public void addItem(Media media, boolean notify, boolean top){
        Storage storage = new Storage(media.getUri());
        addItem(storage, notify, top);
    }

    private void removeDir(final String path) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //if media dir list was empty, we add all others
                if (mMediaDirsLocation.isEmpty()) {
                    Storage storage;
                    String pathString;
                    for (Object item : mMediaList){
                        storage = (Storage) item;
                        if (!TextUtils.equals(path, storage.getUri().getPath())) {
                            pathString = storage.getUri().getPath();
                            mDbManager.addDir(pathString);
                        }
                    }
                } else {
                    mDbManager.removeDir(path);
                }
                updateMediaDirs();
                if (isRoot && mMediaDirsLocation.isEmpty() && getItemCount() > 1)
                    refreshFragment();
            }
        }).start();
    }

    private void addDir(final String path) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDbManager.addDir(path);
                //No need to check for parents for now
//                String parentPath = Strings.getParent(path);
//                while (parentPath != null && !TextUtils.equals(parentPath, "/")) {
//                    mDbManager.removeDir(parentPath);
//                    parentPath = Strings.getParent(parentPath);
//                }
                //Remove subfolders, it would be redundant
                for (String customDirPath : mMediaDirsLocation) {
                    if (customDirPath.startsWith(path+"/"))
                        mDbManager.removeDir(customDirPath);
                }
                updateMediaDirs();
            }
        }).start();
    }

    void refreshFragment(){
        fragment.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mMediaDirsLocation == null || mMediaDirsLocation.isEmpty())
                    fragment.refresh();
            }
        });
    }
}
