/*
 * *************************************************************************
 *  SavePlaylist.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioBrowserListAdapter;

import java.util.ArrayList;

public class SavePlaylist extends DialogFragment implements AdapterView.OnItemClickListener, View.OnClickListener, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/SavePlaylist";

    public static final String KEY_TRACKS = "PLAYLIST_TRACKS";

    EditText mEditText;
    ListView mListView;
    TextView mEmptyView;
    Button mSaveButton;
    AudioBrowserListAdapter mAdapter;
    ArrayList<MediaWrapper> mTracks;

    public SavePlaylist(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new AudioBrowserListAdapter(getActivity(), AudioBrowserListAdapter.ITEM_WITHOUT_COVER);
        mAdapter.addAll(MediaLibrary.getInstance().getPlaylistDbItems());
        mTracks = getArguments().getParcelableArrayList(KEY_TRACKS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        View view = inflater.inflate(R.layout.dialog_playlist, container);

        mListView = (ListView) view.findViewById(android.R.id.list);
        mSaveButton = (Button) view.findViewById(R.id.dialog_playlist_save);
        mEmptyView = (TextView) view.findViewById(android.R.id.empty);
        mEditText = (EditText) view.findViewById(R.id.dialog_playlist_name);
        mListView.setOnItemClickListener(this);
        mSaveButton.setOnClickListener(this);

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
        final MediaDatabase db = MediaDatabase.getInstance();
        final String name = mEditText.getText().toString().trim();
        if (db.playlistExists(name))
            db.playlistDelete(name);
        db.playlistAdd(name);
        MediaWrapper mw;
        for (int i = 0 ; i< mTracks.size() ; ++i){
            mw = mTracks.get(i);
            db.playlistInsertItem(name, i, mw.getLocation());
        }
        dismiss();
    }
}
