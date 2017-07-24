package org.videolan.vlc;

import android.support.v7.widget.RecyclerView;

import org.videolan.vlc.gui.BaseQueuedAdapter;
import org.videolan.vlc.util.MediaLibraryItemComparator;

import java.util.ArrayList;


public abstract class SortableAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseQueuedAdapter<T, VH> {
    private static final String TAG = "VLC/SortableAdapter";
    public static final MediaLibraryItemComparator sMediaComparator = new MediaLibraryItemComparator(SortableAdapter.class);
    private int mCurrentSort = -1, mCurrentDirection = -1;

    public int sortDirection(int sortby) {
        return sMediaComparator.sortDirection(sortby);
    }

    public int getSortDirection() {
        return sMediaComparator.sortDirection;
    }

    public int getSortBy() {
        return sMediaComparator.sortBy;
    }

    public void sortBy(int sortby, int direction) {
        mCurrentDirection = direction;
        mCurrentSort = sortby;
        sMediaComparator.sortBy(sortby, direction);
        update(new ArrayList<>(mDataset), true);
    }

    public void updateIfSortChanged() {
        if (hasSortChanged())
            update(new ArrayList<>(mDataset), true);
    }

    protected boolean hasSortChanged() {
        return mCurrentSort != getSortBy() || mCurrentDirection != getSortDirection();
    }

    @Override
    protected void onUpdateFinished() {
        mCurrentDirection = getSortDirection();
        mCurrentSort = getSortBy();
    }
}
