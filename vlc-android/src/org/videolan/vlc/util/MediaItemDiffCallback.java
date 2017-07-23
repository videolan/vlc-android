package org.videolan.vlc.util;

import android.support.v7.util.DiffUtil;

import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.List;


public class MediaItemDiffCallback extends DiffUtil.Callback {
    private static final String TAG = "MediaItemDiffCallback";
    private List<? extends MediaLibraryItem> oldList, newList;

    public MediaItemDiffCallback(List<? extends MediaLibraryItem> oldList, List<? extends MediaLibraryItem> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList == null ? 0 :oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList == null ? 0 : newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        MediaLibraryItem oldItem = oldList.get(oldItemPosition);
        MediaLibraryItem newItem = newList.get(newItemPosition);
        return oldItem == newItem || ((oldItem == null ) == (newItem == null) && oldItem.equals(newItem));
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return true;
    }
}
