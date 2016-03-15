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

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;

import java.util.ArrayList;

public class AudioAlbumFragment extends PlaybackServiceFragment implements AdapterView.OnItemClickListener, View.OnClickListener {

    public final static String TAG = "VLC/AudioAlbumFragment";

    private AlbumAdapter mAdapter;
    private ArrayList<MediaWrapper> mMediaList;
    private String mTitle;
    Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
            setMediaList(savedInstanceState.<MediaWrapper>getParcelableArrayList("list"), savedInstanceState.getString("title"));
        mAdapter = new AlbumAdapter(getActivity(), mMediaList);

        mAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void setMediaList(ArrayList<MediaWrapper> mediaList, String title) {
        this.mMediaList = mediaList;
        mTitle = title;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_album, container, false);

        v.findViewById(R.id.album_play).setOnClickListener(this);
        ListView songsList = (ListView) v.findViewById(R.id.songs);
        songsList.setAdapter(mAdapter);
        songsList.setOnItemClickListener(this);
        registerForContextMenu(songsList);

        ImageView coverView = (ImageView) v.findViewById(R.id.album_cover);
        Bitmap cover = AudioUtil.getCover(container.getContext(), mMediaList.get(0), 512);
        if (cover != null)
            coverView.setImageBitmap(cover);

        getActivity().setTitle(mTitle);
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateList();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("list", mMediaList);
        outState.putString("title", mTitle);
        super.onSaveInstanceState(outState);
    }

    private void updateList() {
        if (mMediaList == null)
            return;

        mAdapter.clear();
        mAdapter.addAll(mMediaList);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mService != null)
            mService.load(mMediaList, position);
    }

    AlbumAdapter.ContextPopupMenuListener mContextPopupMenuListener
            = new AlbumAdapter.ContextPopupMenuListener() {

        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public void onPopupMenu(View anchor, final int position) {
            if (!AndroidUtil.isHoneycombOrLater()) {
                // Call the "classic" context menu
                anchor.performLongClick();
                return;
            }

            PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
            popupMenu.getMenuInflater().inflate(R.menu.audio_list_browser, popupMenu.getMenu());
            setContextMenuItems(popupMenu.getMenu(), anchor, position);

            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return handleContextItemSelected(item, position);
                }
            });
            popupMenu.show();
        }

    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);
        int position = 0;
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo)
            position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
        setContextMenuItems(menu, v, position);
    }

    private void setContextMenuItems(Menu menu, View v, int position) {
        menu.setGroupVisible(R.id.songs_view_only, true);
        menu.findItem(R.id.audio_list_browser_play_all).setVisible(false);
        menu.setGroupVisible(R.id.phone_only, AndroidDevices.isPhone());
        //Hide delete if we cannot
        String location = mMediaList.get(position).getLocation();
        menu.findItem(R.id.audio_list_browser_delete).setVisible(FileUtils.canWrite(location));
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if(!getUserVisibleHint())
            return super.onContextItemSelected(menu);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menu.getMenuInfo();
        if (info != null && handleContextItemSelected(menu, info.position))
            return true;
        return super.onContextItemSelected(menu);
    }

    private boolean handleContextItemSelected(MenuItem item, final int position) {
        int id = item.getItemId();

        if (mMediaList.size() <= position) {
            Log.e(TAG, "handleContextItemSelected: wrong index. Shouldn't happen !");
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            AudioUtil.setRingtone(mMediaList.get(position), getActivity());
            return true;
        } else if (id == R.id.audio_list_browser_append) {
            mService.append(mMediaList.get(position));
            return true;
        } else if (id == R.id.audio_list_browser_delete) {
            final MediaWrapper media = mAdapter.getItem(position);
            mAdapter.removeMedia(position);
            UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                @Override
                public void run() {
                    deleteMedia(media);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mAdapter.addMedia(position, media);
                }
            });
            return true;
        } else if (id == R.id.audio_view_info) {
                Intent i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra("fragment", "mediaInfo");
                i.putExtra("param", mMediaList.get(position).getUri().toString());
                getActivity().startActivityForResult(i, MainActivity.ACTIVITY_RESULT_SECONDARY);
                return true;
        } else if (id == R.id .audio_view_add_playlist) {
            ArrayList<MediaWrapper> medias = new ArrayList<>();
            medias.add(mAdapter.getItem(position));
            FragmentManager fm = getActivity().getSupportFragmentManager();
            SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
            Bundle args = new Bundle();
            args.putParcelableArrayList(SavePlaylistDialog.KEY_NEW_TRACKS, medias);
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void deleteMedia(final MediaWrapper mw) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                mMediaList.remove(mw);
                final String path = mw.getUri().getPath();
                FileUtils.deleteFile(path);
                MediaDatabase.getInstance().removeMedia(mw.getUri());
                MediaLibrary.getInstance().getMediaItems().remove(mw);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mService != null)
                            mService.removeLocation(mw.getLocation());
                    }
                });
            }
        });
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch (id){
            case R.id.album_play:
                if (mService != null)
                    mService.load(mMediaList, 0);
                break;
            default:
                break;
        }
    }
}
