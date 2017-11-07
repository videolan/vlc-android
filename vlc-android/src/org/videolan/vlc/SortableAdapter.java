package org.videolan.vlc;

import android.support.v7.widget.RecyclerView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.util.MediaLibraryItemComparator;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;


public abstract class SortableAdapter<T extends MediaLibraryItem, VH extends RecyclerView.ViewHolder> extends DiffUtilAdapter<T, VH> {
    private static final String TAG = "VLC/SortableAdapter";

    public static MediaLibraryItemComparator getComparator() {
        return Holder.mediaComparator;
    }

    private int mCurrentSort = MediaLibraryItemComparator.SORT_DEFAULT, mCurrentDirection = getDefaultDirection();

    public int sortDirection(int sortby) {
        return mCurrentSort == MediaLibraryItemComparator.SORT_DEFAULT ? mCurrentDirection : getComparator().sortDirection(sortby);
    }

    public int getSortDirection() {
        return getComparator().sortDirection;
    }

    public int getSortBy() {
        return getComparator().sortBy;
    }

    public void sortBy(int sortby, int direction) {
        getComparator().sortBy(sortby, direction);
        update(new ArrayList<>(peekLast()));
        mCurrentDirection = getSortDirection();
        mCurrentSort = getSortBy();
    }

    public void updateIfSortChanged() {
        if (!hasPendingUpdates() && hasSortChanged())
            update(new ArrayList<>(getDataset()));
    }

    private boolean hasSortChanged() {
        return mCurrentSort != MediaLibraryItemComparator.SORT_DEFAULT
                && (mCurrentSort != getSortBy() || mCurrentDirection != getSortDirection());
    }

    protected boolean needsSorting() {
        return mCurrentSort != MediaLibraryItemComparator.SORT_DEFAULT
                && getComparator().sortBy != MediaLibraryItemComparator.SORT_DEFAULT && isSortAllowed(getComparator().sortBy);
    }

    @Override
    protected void onUpdateFinished() {
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

    protected ArrayList<T> prepareList(ArrayList<T> list) {
        if (needsSorting())
            Collections.sort(list, getComparator());
        return list;
    }

    public void add(final T[] items) {
        if (!Util.isArrayEmpty(items)) {
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (getSortBy() == MediaLibraryItemComparator.SORT_DEFAULT)
                        getComparator().sortBy(getDefaultSort(), 1);
                    final ArrayList<T> list = new ArrayList<>(peekLast());
                    VLCApplication.runBackground(new Runnable() {
                        @Override
                        public void run() {
                            Util.insertOrUdpate(list, items);
                                    update(list);
                        }
                    });
                }
            });
        }
    }

    private static class Holder {
        private static final MediaLibraryItemComparator mediaComparator = new MediaLibraryItemComparator(SortableAdapter.class);
    }
}
