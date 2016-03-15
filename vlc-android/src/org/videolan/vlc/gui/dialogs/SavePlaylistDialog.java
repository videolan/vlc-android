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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserListAdapter;

import java.util.ArrayList;

public class SavePlaylistDialog extends DialogFragment implements AdapterView.OnItemClickListener, View.OnClickListener, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/SavePlaylistDialog";

    public static final String KEY_TRACKS = "PLAYLIST_TRACKS";
    public static final String KEY_NEW_TRACKS = "PLAYLIST_TRACKS";

    EditText mEditText;
    ListView mListView;
    TextView mEmptyView;
    Button mSaveButton;
    Button mCancelButton;
    AudioBrowserListAdapter mAdapter;
    ArrayList<MediaWrapper> mTracks;
    ArrayList<MediaWrapper> mNewTrack;
    Runnable mCallBack;

    public SavePlaylistDialog(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITHOUT_COVER);
        mAdapter.addAll(MediaLibrary.getInstance().getPlaylistDbItems());
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

        mListView = (ListView) view.findViewById(android.R.id.list);
        mSaveButton = (Button) view.findViewById(R.id.dialog_playlist_save);
        mCancelButton = (Button) view.findViewById(R.id.dialog_playlist_cancel);
        mEmptyView = (TextView) view.findViewById(android.R.id.empty);
        TextInputLayout mLayout = (TextInputLayout)view.findViewById(R.id.dialog_playlist_name);
        mLayout.setHint(getString(R.string.playlist_name_hint));
        mEditText = mLayout.getEditText();
        mListView.setOnItemClickListener(this);
        mSaveButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mEditText.setOnEditorActionListener(this);
        mListView.setEmptyView(mEmptyView);
        mListView.setAdapter(mAdapter);
        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mEditText.setText(mAdapter.getItem(position).mTitle);
    }

    @Override
    public void onClick(View v) {
        savePlaylist();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            savePlaylist();
        }
        return false;
    }

    private void savePlaylist() {
        VLCApplication.runBackground(new Runnable() {
            public void run() {
                final MediaDatabase db = MediaDatabase.getInstance();
                final String name = mEditText.getText().toString().trim();
                boolean addTracks = mNewTrack != null;
                boolean exists = db.playlistExists(name);
                if (addTracks) {
                    int position = 0;
                    if (!exists)
                        db.playlistAdd(name);
                    else
                        position = db.playlistGetItems(name).length;
                    for (int i = 0 ; i < mNewTrack.size(); ++i)
                        db.playlistInsertItem(name, position+i, mNewTrack.get(i).getLocation());
                } else { //Save a playlist
                    if (exists)
                        db.playlistDelete(name);
                    db.playlistAdd(name);
                    MediaWrapper mw;
                    for (int i = 0; i < mTracks.size(); ++i) {
                        mw = mTracks.get(i);
                        db.playlistInsertItem(name, i, mw.getLocation());
                    }
                }
                if (mCallBack != null)
                    mCallBack.run();
            }
        });
        dismiss();
    }
}
