/*
 * *************************************************************************
 *  BaseAudioBrowser.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
import android.view.Menu;
import android.view.MenuItem;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.AndroidDevices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseAudioBrowser extends MediaBrowserFragment {

    abstract protected AudioBrowserAdapter getCurrentAdapter();

    protected void inflate(Menu menu, int position) {
        if (getActivity() == null)
            return;
        getActivity().getMenuInflater().inflate(R.menu.audio_list_browser, menu);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_audio_browser, menu);
        if (playlistModeSelected())
            menu.findItem(R.id.action_mode_audio_add_playlist).setVisible(false);
        getCurrentAdapter().setActionMode(true);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (getCurrentAdapter().getSelectedPositions().isEmpty()) {
            stopActionMode();
            return false;
        }
        boolean oneSong = songModeSelected() && getCurrentAdapter().getSelectedPositions().size() == 1;
        menu.findItem(R.id.action_mode_audio_set_song).setVisible(oneSong && AndroidDevices.isPhone());
        menu.findItem(R.id.action_mode_audio_info).setVisible(oneSong);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        List<MediaLibraryItem> list = getCurrentAdapter().getSelection();
        ArrayList<MediaWrapper> tracks = new ArrayList<>();
        for (MediaLibraryItem mediaItem : list)
            tracks.addAll(Arrays.asList(mediaItem.getTracks(mMediaLibrary)));
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
                showInfoDialog((MediaWrapper) getCurrentAdapter().getSelection().get(0));
                break;
            case R.id.action_mode_audio_set_song:
                AudioUtil.setRingtone((MediaWrapper) getCurrentAdapter().getSelection().get(0), getActivity());
                break;
            default:
                stopActionMode();
                return false;
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        getCurrentAdapter().setActionMode(false);
    }

    @Override
    public void onRefresh() {}

    protected boolean songModeSelected() {
        return true;
    }

    protected boolean playlistModeSelected() {
        return false;
    }
}
