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

import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.ContentActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.viewmodels.audio.AudioModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseAudioBrowser extends MediaBrowserFragment<AudioModel> implements IEventsHandler {

    public ContentActivity mActivity;
    protected AudioBrowserAdapter[] mAdapters;
    protected AudioBrowserAdapter mAdapter;

    public AudioBrowserAdapter getCurrentAdapter() {
        return mAdapter;
    }

    protected void inflate(Menu menu, int position) {
        if (getActivity() == null) return;
        getActivity().getMenuInflater().inflate(R.menu.audio_list_browser, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_last_playlist:
                MediaUtils.loadlastPlaylist(getActivity(), Constants.PLAYLIST_TYPE_AUDIO);
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
        menu.findItem(R.id.action_mode_audio_append).setVisible(PlaylistManager.Companion.hasMedia());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        final List<MediaLibraryItem> list = getCurrentAdapter().getSelection();
        stopActionMode();
        if (!list.isEmpty()) {
            final List<MediaWrapper> tracks = new ArrayList<>();
            for (MediaLibraryItem mediaItem : list)
                tracks.addAll(Arrays.asList(mediaItem.getTracks()));
            switch (item.getItemId()) {
                case R.id.action_mode_audio_play:
                    MediaUtils.openList(getActivity(), tracks, 0);
                    break;
                case R.id.action_mode_audio_append:
                    MediaUtils.appendMedia(getActivity(), tracks);
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
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        UiTools.updateSortTitles(this);
    }
}
