/*
 * *************************************************************************
 *  SavePlaylist.java
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

package org.videolan.vlc.gui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserAdapter;

import java.util.ArrayList;

public class SavePlaylistDialog extends DialogFragment implements View.OnClickListener, TextView.OnEditorActionListener, AudioBrowserAdapter.EventsHandler {

    public final static String TAG = "VLC/SavePlaylistDialog";

    public static final String KEY_TRACKS = "PLAYLIST_TRACKS";
    public static final String KEY_NEW_TRACKS = "PLAYLIST_TRACKS";

    EditText mEditText;
    RecyclerView mListView;
    TextView mEmptyView;
    Button mSaveButton;
    Button mCancelButton;
    AudioBrowserAdapter mAdapter;
    ArrayList<MediaWrapper> mTracks;
    ArrayList<MediaWrapper> mNewTrack;
    Runnable mCallBack;
    Medialibrary mMedialibrary;
    long mPlaylistId;

    public SavePlaylistDialog(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMedialibrary = VLCApplication.getMLInstance();
        mAdapter = new AudioBrowserAdapter(getActivity(), this, false);
        mAdapter.addAll(mMedialibrary.getPlaylists());
        mTracks = getArguments().getParcelableArrayList(KEY_TRACKS);
        mNewTrack = getArguments().getParcelableArrayList(KEY_NEW_TRACKS);
    }

    public void setCallBack(Runnable cb) {
        mCallBack = cb;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
        dialog.setTitle(R.string.playlist_save);
        return dialog;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_playlist, container);

        mListView = (RecyclerView) view.findViewById(android.R.id.list);
        mSaveButton = (Button) view.findViewById(R.id.dialog_playlist_save);
        mCancelButton = (Button) view.findViewById(R.id.dialog_playlist_cancel);
        mEmptyView = (TextView) view.findViewById(android.R.id.empty);
        TextInputLayout mLayout = (TextInputLayout)view.findViewById(R.id.dialog_playlist_name);
        mEditText = mLayout.getEditText();
        mSaveButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mEditText.setOnEditorActionListener(this);
        mListView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mListView.setAdapter(mAdapter);
        mEmptyView.setVisibility(mAdapter.isEmpty() ? View.VISIBLE : View.GONE);
        return view;
    }

    @Override
    public void onClick(View v) {
        savePlaylist();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND)
            savePlaylist();
        return false;
    }

    private void savePlaylist() {
        VLCApplication.runBackground(new Runnable() {
            public void run() {
                final String name = mEditText.getText().toString().trim();
                boolean addTracks = mNewTrack != null;
                Playlist playlist = mMedialibrary.getPlaylist(mPlaylistId);
                boolean exists = playlist != null;
                long[] ids;
                if (addTracks) {
                    if (!exists)
                        playlist = mMedialibrary.createPlaylist(name);
                    ids = new long[mTracks.size()];
                    for (int i = 0 ; i < mNewTrack.size(); ++i)
                        ids[i] = mNewTrack.get(i).getId();
                } else { //Save a playlist
                    if (exists)
                        playlist.delete(mMedialibrary);
                    playlist = mMedialibrary.createPlaylist(name);
                    ids = new long[mTracks.size()];
                    for (int i = 0; i < mTracks.size(); ++i)
                        ids[i] = mTracks.get(i).getId();
                }
                playlist.append(mMedialibrary, ids);
                if (mCallBack != null)
                    mCallBack.run();
            }
        });
        dismiss();
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        mPlaylistId = item.getId();
        mEditText.setText(item.getTitle());
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {}

    @Override
    public void startActionMode() {}

    @Override
    public void invalidateActionMode() {}
}
