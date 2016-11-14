/*
 * *************************************************************************
 *  AudioAlbumFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;

import java.util.ArrayList;

public class AudioAlbumFragment extends BaseAudioBrowser implements View.OnClickListener, AudioBrowserAdapter.EventsHandler {

    public final static String TAG = "VLC/AudioAlbumFragment";

    public final static String TAG_ITEM = "ML_ITEM";

    private AudioBrowserAdapter mAdapter;
    private Album mAlbum;
    private Medialibrary mMediaLibrary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();

        mAlbum = (Album) (savedInstanceState != null ?
                savedInstanceState.getParcelable(TAG_ITEM) :
                getArguments().getParcelable(TAG_ITEM));
        mAdapter = new AudioBrowserAdapter(getActivity(), this, false);
    }

    @Override
    protected String getTitle() {
        return mAlbum.getTitle();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_album, container, false);

        v.findViewById(R.id.album_play).setOnClickListener(this);
        RecyclerView songsList = (RecyclerView) v.findViewById(R.id.songs);
        songsList.setLayoutManager(new LinearLayoutManager(v.getContext()));
        songsList.setAdapter(mAdapter);
        registerForContextMenu(songsList);
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateList();

        ImageView coverView = (ImageView) view.findViewById(R.id.album_cover);
        Bitmap cover = AudioUtil.readCoverBitmap(mAlbum.getArtworkMrl(), 64);
        if (cover != null)
            coverView.setImageBitmap(cover);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(TAG_ITEM, mAlbum);
        super.onSaveInstanceState(outState);
    }

    private void updateList() {
        if (mAlbum == null)
            return;

        mAdapter.clear();
        mAdapter.addAll(mAlbum.getTracks(mMediaLibrary));
    }

    @Override
    protected void setContextMenuItems(Menu menu, int position) {
        menu.setGroupVisible(R.id.songs_view_only, true);
        menu.findItem(R.id.audio_list_browser_play_all).setVisible(false);
        menu.setGroupVisible(R.id.phone_only, AndroidDevices.isPhone());
        //Hide delete if we cannot
        String location = ((MediaWrapper)mAdapter.getItem(position)).getLocation();
        menu.findItem(R.id.audio_list_browser_delete).setVisible(FileUtils.canWrite(location));
    }

    @Override
    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        int id = item.getItemId();

        if (mAlbum.getNbTracks() <= position) {
            Log.e(TAG, "handleContextItemSelected: wrong index. Shouldn't happen !");
            return true;
        }

        final MediaWrapper media = (MediaWrapper) mAdapter.getItem(position);

        if (id == R.id.audio_list_browser_set_song) {
            AudioUtil.setRingtone(media, getActivity());
            return true;
        } else if (id == R.id.audio_list_browser_append) {
            mService.append(media);
            return true;
        } else if (id == R.id.audio_list_browser_delete) {
            mAdapter.remove(position);
            UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(position);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mAdapter.addItem(position, media);
                }
            });
            return true;
        } else if (id == R.id.audio_view_info) {
            showInfoDialog(media);
            return true;
        } else if (id == R.id.audio_view_add_playlist) {
            ArrayList<MediaWrapper> medias = new ArrayList<>();
            medias.add(media);
            FragmentManager fm = getActivity().getSupportFragmentManager();
            SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
            Bundle args = new Bundle();
            args.putParcelableArrayList(SavePlaylistDialog.KEY_NEW_TRACKS, medias);
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }
        return false;
    }

    private void deleteMedia(final int position) {
        mAdapter.remove(position);
        super.deleteMedia(mAdapter.getItem(position), true);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch (id){
            case R.id.album_play:
                if (mService != null)
                    mService.load(mAlbum.getTracks(mMediaLibrary), 0);
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (mService != null)
            mService.load(mAlbum.getTracks(mMediaLibrary), position);
    }

    @Override
    public void onCtxClick(View anchor, final int position, final MediaLibraryItem mediaItem) {
        ((ContextMenuRecyclerView) getView().findViewById(R.id.songs)).openContextMenu(position);
    }

    @Override
    protected AudioBrowserAdapter getCurrentAdapter() {
        return mAdapter;
    }
}
