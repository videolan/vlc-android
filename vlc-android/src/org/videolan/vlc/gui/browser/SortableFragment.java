/*
 * *************************************************************************
 *  SortableFragment.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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


import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import org.videolan.vlc.R;
import org.videolan.vlc.SortableAdapter;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.MediaLibraryItemComparator;

public abstract class SortableFragment<T extends SortableAdapter> extends MediaBrowserFragment {
    protected T mAdapter;

    public T getCurrentAdapter() {
        return mAdapter;
    }

    public int getSortBy() {
        return getCurrentAdapter().getSortBy();
    }

    public int getDefaultSort() {
        return getCurrentAdapter().getDefaultSort();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        final T adapter = getCurrentAdapter();
        if (adapter != null) adapter.updateIfSortChanged();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_sortby).setVisible(isSortEnabled());
        UiTools.updateSortTitles(this, menu);
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.findItem(R.id.ml_menu_sortby).setVisible(true);
        return super.onPrepareActionMode(mode, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
                sortBy(MediaLibraryItemComparator.SORT_BY_TITLE);
                onPrepareOptionsMenu(mMenu);
                return true;
            case R.id.ml_menu_sortby_artist_name:
                sortBy(MediaLibraryItemComparator.SORT_BY_ARTIST);
                onPrepareOptionsMenu(mMenu);
                return true;
            case R.id.ml_menu_sortby_album_name:
                sortBy(MediaLibraryItemComparator.SORT_BY_ALBUM);
                onPrepareOptionsMenu(mMenu);
                return true;
            case R.id.ml_menu_sortby_length:
                sortBy(MediaLibraryItemComparator.SORT_BY_LENGTH);
                onPrepareOptionsMenu(mMenu);
                return true;
            case R.id.ml_menu_sortby_date:
                sortBy(MediaLibraryItemComparator.SORT_BY_DATE);
                onPrepareOptionsMenu(mMenu);
                return true;
            case R.id.ml_menu_sortby_number:
                sortBy(MediaLibraryItemComparator.SORT_BY_NUMBER);
                onPrepareOptionsMenu(mMenu);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean isSortEnabled() {
        return true;
    }

    public void sortBy(int sortby) {
        int sortDirection = getCurrentAdapter().getSortDirection();
        int sortBy = getCurrentAdapter().getSortBy();
        if (sortBy == MediaLibraryItemComparator.SORT_DEFAULT)
            sortBy = getDefaultSort();
        if (sortby == sortBy)
            sortDirection*=-1;
        else
            sortDirection = 1;
        getCurrentAdapter().sortBy(sortby, sortDirection);
    }

    public int sortDirection(int sortby) {
        return getCurrentAdapter().sortDirection(sortby);
    }
}
