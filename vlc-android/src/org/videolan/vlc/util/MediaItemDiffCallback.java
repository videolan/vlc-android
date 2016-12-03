package org.videolan.vlc.util;

import android.support.v7.util.DiffUtil;

import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.List;


public class MediaItemDiffCallback extends DiffUtil.Callback {
    private static final String TAG = "MediaItemDiffCallback";
    MediaLibraryItem[] oldList, newList;

    public MediaItemDiffCallback(List<? extends MediaLibraryItem> oldList, List<? extends MediaLibraryItem> newList) {
        this.oldList = oldList.toArray(new MediaLibraryItem[oldList.size()]);
        this.newList = newList.toArray(new MediaLibraryItem[newList.size()]);
    }

    public MediaItemDiffCallback(MediaLibraryItem[] oldList, MediaLibraryItem[] newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList == null ? 0 :oldList.length;
    }

    @Override
    public int getNewListSize() {
        return newList == null ? 0 : newList.length;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList[oldItemPosition].equals(newList[newItemPosition]);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return true;
    }
}
