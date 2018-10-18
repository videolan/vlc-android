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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AppCompatDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.SimpleAdapter;
import org.videolan.vlc.util.WorkersKt;

import java.util.Arrays;
import java.util.LinkedList;

public class SavePlaylistDialog extends DialogFragment implements View.OnClickListener, TextView.OnEditorActionListener, SimpleAdapter.ClickHandler {

    public final static String TAG = "VLC/SavePlaylistDialog";

    public static final String KEY_TRACKS = "PLAYLIST_TRACKS";
    public static final String KEY_NEW_TRACKS = "PLAYLIST_NEW_TRACKS";

    EditText mEditText;
    RecyclerView mListView;
    TextView mEmptyView;
    Button mSaveButton;
    Button mCancelButton;
    SimpleAdapter mAdapter;
    MediaWrapper[] mTracks;
    MediaWrapper[] mNewTrack;
    Runnable mCallBack;
    Medialibrary mMedialibrary;
    long mPlaylistId;

    public SavePlaylistDialog(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMedialibrary = VLCApplication.getMLInstance();
        mAdapter = new SimpleAdapter(this);
        mTracks = (MediaWrapper[]) getArguments().getParcelableArray(KEY_TRACKS);
        mNewTrack = (MediaWrapper[]) getArguments().getParcelableArray(KEY_NEW_TRACKS);
    }

    public void setCallBack(Runnable cb) {
        mCallBack = cb;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
        dialog.setTitle(R.string.playlist_save);
        return dialog;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlist, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = view.findViewById(android.R.id.list);
        mSaveButton = view.findViewById(R.id.dialog_playlist_save);
        mCancelButton = view.findViewById(R.id.dialog_playlist_cancel);
        mEmptyView = view.findViewById(android.R.id.empty);
        TextInputLayout mLayout = view.findViewById(R.id.dialog_playlist_name);
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
        mAdapter.submitList(Arrays.<MediaLibraryItem>asList(mMedialibrary.getPlaylists()));
        updateEmptyView();
    }

    void updateEmptyView() {
        mEmptyView.setVisibility(mAdapter.isEmpty() ? View.VISIBLE : View.GONE);
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
        WorkersKt.runIO(new Runnable() {
            public void run() {
                final String name = mEditText.getText().toString().trim();
                final boolean addTracks = !Tools.isArrayEmpty(mNewTrack);
                Playlist playlist = mMedialibrary.getPlaylist(mPlaylistId);
                boolean exists = playlist != null;
                MediaWrapper[] tracks;
                if (!exists) playlist = mMedialibrary.createPlaylist(name);
                if (playlist == null) return;
                if (addTracks) {
                    tracks = mNewTrack;
                } else {//Save a playlist
                    for (MediaWrapper mw : playlist.getTracks()) {
                        playlist.remove(mw.getId());
                    }
                    tracks = mTracks;
                }
                if (tracks == null) return;
                final LinkedList<Long> ids = new LinkedList<>();
                for (MediaWrapper mw : tracks) {
                    long id = mw.getId();
                    if (id == 0) {
                        MediaWrapper media = mMedialibrary.getMedia(mw.getUri());
                        if (media != null) ids.add(media.getId());
                        else {
                            media = mMedialibrary.addMedia(mw.getLocation());
                            if (media != null) ids.add(media.getId());
                        }
                    } else ids.add(id);
                }
                playlist.append(ids);
                if (mCallBack != null) mCallBack.run();
            }
        });
        dismiss();
    }

//    @Override
//    public void onUpdateFinished(RecyclerView.Adapter adapter) {
//        updateEmptyView();
//    }

    @Override
    public void onClick(@NotNull MediaLibraryItem item) {
        mPlaylistId = item.getId();
        mEditText.setText(item.getTitle());
    }
}
