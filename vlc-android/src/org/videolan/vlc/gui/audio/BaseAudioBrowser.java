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

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.ContentActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.dialogs.ContextSheetKt;
import org.videolan.vlc.gui.dialogs.CtxActionReceiver;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.ModelsHelperKt;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.paged.MLPagedModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseAudioBrowser extends MediaBrowserFragment<MLPagedModel> implements IEventsHandler, CtxActionReceiver {

    ContentActivity mActivity;
    AudioBrowserAdapter[] mAdapters;
    protected AudioBrowserAdapter mAdapter;

    public AudioBrowserAdapter getCurrentAdapter() {
        return mAdapter;
    }

    abstract protected RecyclerView getCurrentRV();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (ContentActivity) context;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_last_playlist:
                MediaUtils.INSTANCE.loadlastPlaylist(getActivity(), Constants.PLAYLIST_TYPE_AUDIO);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_audio_browser, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        final List<MediaLibraryItem> selection = getCurrentAdapter().getMultiSelectHelper().getSelection();
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
    public boolean onActionItemClicked(ActionMode mode, final MenuItem item) {
        final List<MediaLibraryItem> list = getCurrentAdapter().getMultiSelectHelper().getSelection();
        stopActionMode();
        if (!list.isEmpty()) {
            WorkersKt.runIO(new Runnable() {
                @Override
                public void run() {
                    final List<MediaWrapper> tracks = new ArrayList<>();
                    for (MediaLibraryItem mediaItem : list)
                        tracks.addAll(Arrays.asList(mediaItem.getTracks()));
                    WorkersKt.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            switch (item.getItemId()) {
                                case R.id.action_mode_audio_play:
                                    MediaUtils.INSTANCE.openList(getActivity(), tracks, 0);
                                    break;
                                case R.id.action_mode_audio_append:
                                    MediaUtils.INSTANCE.appendMedia(getActivity(), tracks);
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
                            }
                        }
                    });
                }
            });
        }
        return true;
    }

    public void onDestroyActionMode(ActionMode actionMode) {
        onDestroyActionMode(getCurrentAdapter());
    }

    void onDestroyActionMode(AudioBrowserAdapter adapter) {
        setFabPlayVisibility(true);
        mActionMode = null;
        adapter.getMultiSelectHelper().clearSelection();
    }

    @Override
    protected void sortBy(int sort) {
        if (ModelsHelperKt.canSortBy(getViewModel(), sort)) getCurrentAdapter().setSort(sort);
        super.sortBy(sort);
    }

    @Override
    public void onRefresh() {}

    @Override
    public void onClick(@NonNull View v, int position, @NonNull MediaLibraryItem item) {
        if (mActionMode != null) {
            getCurrentAdapter().getMultiSelectHelper().toggleSelection(position);
            invalidateActionMode();
        }
    }

    @Override
    public boolean onLongClick(View v, int position, @NonNull MediaLibraryItem item) {
        if (mActionMode != null) return false;
        getCurrentAdapter().getMultiSelectHelper().toggleSelection(position);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(@NonNull View anchor, final int position, @NonNull MediaLibraryItem item) {
        final int flags;
        switch (item.getItemType()) {
            case MediaLibraryItem.TYPE_MEDIA:
                flags = Constants.CTX_TRACK_FLAGS;
                break;
            case MediaLibraryItem.TYPE_PLAYLIST:
                flags = Constants.CTX_PLAYLIST_FLAGS;
                break;
            default:
                flags = Constants.CTX_AUDIO_FLAGS;
        }
        if (mActionMode == null) ContextSheetKt.showContext(requireActivity(), this, position, item.getTitle(), flags);
    }

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        UiTools.updateSortTitles(this);
    }

    @Override
    public void onCtxAction(int position, int option) {
        final AudioBrowserAdapter adapter = getCurrentAdapter();
        if (position >= adapter.getItemCount()) return;
        final MediaLibraryItem media = adapter.getItem(position);
        if (media == null) return;
        switch (option){
            case Constants.CTX_PLAY:
                MediaUtils.INSTANCE.playTracks(requireActivity(), media, 0);
                break;
            case Constants.CTX_PLAY_ALL:
                MediaUtils.INSTANCE.playAll(requireContext(), getViewModel(), position, false);
                break;
            case Constants.CTX_INFORMATION:
                showInfoDialog(media);
                break;
            case Constants.CTX_DELETE:
                removeItem(media);
                break;
            case Constants.CTX_APPEND:
                MediaUtils.INSTANCE.appendMedia(requireActivity(), media.getTracks());
                break;
            case Constants.CTX_PLAY_NEXT:
                MediaUtils.INSTANCE.insertNext(requireActivity(), media.getTracks());
                break;
            case Constants.CTX_ADD_TO_PLAYLIST:
                UiTools.addToPlaylist(requireActivity(), media.getTracks(), SavePlaylistDialog.KEY_NEW_TRACKS);
                break;
            case Constants.CTX_SET_RINGTONE:
                AudioUtil.setRingtone((MediaWrapper) media, requireActivity());
                break;
        }
    }

    final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                mSwipeRefreshLayout.setEnabled(false);
                return;
            }
            final LinearLayoutManager llm = (LinearLayoutManager)getCurrentRV().getLayoutManager();
            if (llm == null) return;
            mSwipeRefreshLayout.setEnabled(llm.findFirstVisibleItemPosition() <= 0);
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {}
    };
}
