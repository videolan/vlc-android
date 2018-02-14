package org.videolan.vlc;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.util.MediaLibraryItemComparator;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public abstract class SortableAdapter<T extends MediaLibraryItem, VH extends RecyclerView.ViewHolder> extends DiffUtilAdapter<T, VH> {
    private static final String TAG = "VLC/SortableAdapter";
    public static final MediaLibraryItemComparator sMediaComparator = new MediaLibraryItemComparator(SortableAdapter.class);
    private int mCurrentSort = -1, mCurrentDirection = 1;

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
        sMediaComparator.sortBy(sortby, direction);
        update(new ArrayList<>(peekLast()));
    }

    public void updateIfSortChanged() {
        final Medialibrary ml = VLCApplication.getMLInstance();
        if (ml.isInitiated() && !ml.isWorking() && !hasPendingUpdates() && hasSortChanged()) update(new ArrayList<>(peekLast()));
    }

    private boolean hasSortChanged() {
        return mCurrentSort != getSortBy() || mCurrentDirection != getSortDirection();
    }

    protected boolean needsSorting() {
        return sMediaComparator.sortBy != MediaLibraryItemComparator.SORT_DEFAULT && isSortAllowed(sMediaComparator.sortBy);
    }

    @Override
    protected void onUpdateFinished() {
        mCurrentDirection = getSortDirection();
        mCurrentSort = getSortBy();
    }

    public int getDefaultSort() {
        return MediaLibraryItemComparator.SORT_BY_TITLE;
    }

    protected int getDefaultDirection() {
        return 1;
    }

    protected boolean isSortAllowed(int sort) {
        return sort == MediaLibraryItemComparator.SORT_BY_TITLE;
    }

    protected boolean detectMoves() {
        return hasSortChanged();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    protected List<T> prepareList(@NotNull List<? extends T> list) {
        if (needsSorting()) Collections.sort(list, sMediaComparator);
        return (List<T>) list;
    }

    public void add(final T[] items) {
        if (!Util.isArrayEmpty(items)) {
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (getSortBy() == MediaLibraryItemComparator.SORT_DEFAULT)
                        sMediaComparator.sortBy(getDefaultSort(), 1);
                    final ArrayList<T> list = new ArrayList<>(peekLast());
                    Util.insertOrUdpate(list, items);
                    update(list);
                }
            });
        }
    }
}
