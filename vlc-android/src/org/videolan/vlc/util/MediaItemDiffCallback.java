package org.videolan.vlc.util;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.gui.DiffUtilAdapter;


public class MediaItemDiffCallback< T extends MediaLibraryItem> extends DiffUtilAdapter.DiffCallback<T> {
    private static final String TAG = "MediaItemDiffCallback";

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        final MediaLibraryItem oldItem = oldList.get(oldItemPosition);
        final MediaLibraryItem newItem = newList.get(newItemPosition);
        return oldItem == newItem || ((oldItem == null ) == (newItem == null) && oldItem.equals(newItem));
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return true;
    }
}
