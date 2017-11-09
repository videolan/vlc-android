/*
 * *************************************************************************
 *  BaseAudioBrowser.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio;

import android.content.Intent;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.ContentActivity;
import org.videolan.vlc.gui.browser.SortableFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.MediaLibraryItemComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseAudioBrowser extends SortableFragment<AudioBrowserAdapter> implements IEventsHandler, Filterable {

    public View mSearchButtonView;
    public ContentActivity mActivity;
    protected AudioBrowserAdapter[] mAdapters;

    protected void inflate(Menu menu, int position) {
        if (getActivity() == null)
            return;
        getActivity().getMenuInflater().inflate(R.menu.audio_list_browser, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        AudioBrowserAdapter adapter = getCurrentAdapter();
        menu.findItem(R.id.ml_menu_last_playlist).setVisible(true);
        menu.findItem(R.id.ml_menu_sortby_name).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_TITLE));
        menu.findItem(R.id.ml_menu_sortby_artist_name).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_ARTIST));
        menu.findItem(R.id.ml_menu_sortby_album_name).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_ALBUM));
        menu.findItem(R.id.ml_menu_sortby_length).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_LENGTH));
        menu.findItem(R.id.ml_menu_sortby_date).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_DATE));
        menu.findItem(R.id.ml_menu_sortby_number).setVisible(adapter.isSortAllowed(MediaLibraryItemComparator.SORT_BY_NUMBER));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_last_playlist:
                getActivity().sendBroadcast(new Intent(Constants.ACTION_REMOTE_LAST_PLAYLIST));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_audio_browser, menu);
        if (playlistModeSelected())
            menu.findItem(R.id.action_mode_audio_add_playlist).setVisible(false);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        final List<MediaLibraryItem> selection = getCurrentAdapter().getSelection();
        final int count = selection.size();
        if (count == 0) {
            stopActionMode();
            return false;
        }
        boolean isSong = count == 1 && selection.get(0).getItemType() == MediaLibraryItem.TYPE_MEDIA;
        menu.findItem(R.id.action_mode_audio_set_song).setVisible(isSong && AndroidDevices.isPhone);
        menu.findItem(R.id.action_mode_audio_info).setVisible(count == 1);
        menu.findItem(R.id.action_mode_audio_append).setVisible(mService.hasMedia());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        List<MediaLibraryItem> list = getCurrentAdapter().getSelection();
        stopActionMode();
        if (!list.isEmpty()) {
            List<MediaWrapper> tracks = new ArrayList<>();
            for (MediaLibraryItem mediaItem : list)
                tracks.addAll(Arrays.asList(mediaItem.getTracks()));
            switch (item.getItemId()) {
                case R.id.action_mode_audio_play:
                    mService.load(tracks, 0);
                    break;
                case R.id.action_mode_audio_append:
                    mService.append(tracks);
                    break;
                case R.id.action_mode_audio_add_playlist:
                    UiTools.addToPlaylist(getActivity(), tracks);
                    break;
                case R.id.action_mode_audio_info:
                    showInfoDialog(list.get(0));
                    break;
                case R.id.action_mode_audio_set_song:
                    AudioUtil.setRingtone((MediaWrapper) list.get(0), getActivity());
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public void onDestroyActionMode(ActionMode actionMode) {
        onDestroyActionMode(getCurrentAdapter());
    }

    public void onDestroyActionMode(AudioBrowserAdapter adapter) {
        setFabPlayVisibility(true);
        mActionMode = null;
        List<? extends MediaLibraryItem> items = adapter.getAll();
        if (items != null) {
            for (int i = 0; i < items.size(); ++i) {
                if (items.get(i).hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                    items.get(i).removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                    adapter.notifyItemChanged(i, items.get(i));
                }
            }
        }
        adapter.resetSelectionCount();
    }

    @Override
    public void onRefresh() {}

    protected boolean playlistModeSelected() {
        return false;
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null) {
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            getCurrentAdapter().updateSelectionCount(item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
            getCurrentAdapter().notifyItemChanged(position, item);
            invalidateActionMode();
        }
    }

    @Override
    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null)
            return false;
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            getCurrentAdapter().updateSelectionCount(item.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        getCurrentAdapter().notifyItemChanged(position, item);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(View anchor, final int position, final MediaLibraryItem mediaItem) {}

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {}

    public void restoreList() {
        final AudioBrowserAdapter adapter = getCurrentAdapter();
        if (adapter != null)
            adapter.restoreList();
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    @Override
    public void setSearchVisibility(boolean visible) {
        UiTools.setViewVisibility(mSearchButtonView, visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void sortBy(int newSortby) {
        final AudioBrowserAdapter adapter = getCurrentAdapter();
        int sortDirection = adapter.getSortDirection();
        final int oldSortby = adapter.getSortBy();
        final int delfaultSortby = adapter.getDefaultSort();
        final int defaultDirection = adapter.getDefaultDirection();
        if (newSortby == oldSortby)
            sortDirection*=-1;
        else if (newSortby == delfaultSortby)
            sortDirection = defaultDirection*-1;
        else
            sortDirection = 1;
        for (AudioBrowserAdapter audioBrowserAdapter : mAdapters)
            audioBrowserAdapter.sortBy(newSortby, sortDirection);
    }
}
