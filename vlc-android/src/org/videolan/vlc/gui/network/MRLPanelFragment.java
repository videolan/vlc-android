/*****************************************************************************
 * MRLPanelFragment.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.network;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.medialibrary.media.HistoryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.DividerItemDecoration;
import org.videolan.vlc.interfaces.IHistory;
import org.videolan.vlc.media.MediaUtils;

public class MRLPanelFragment extends Fragment implements IHistory, View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener {
    private static final String TAG = "VLC/MrlPanelFragment";
    public static final String KEY_MRL = "mrl";
    private RecyclerView mRecyclerView;
    private MRLAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    TextInputLayout mEditText;
    ImageView mSend;
    View mRootView;

    public MRLPanelFragment(){}

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.open_mrl_dialog_title);
        View v = inflater.inflate(R.layout.mrl_panel, container, false);
        mRootView = v.findViewById(R.id.mrl_root);
        mEditText = (TextInputLayout) v.findViewById(R.id.mrl_edit);
        mSend = (ImageView) v.findViewById(R.id.send);
        mEditText.getEditText().setOnKeyListener(this);
        mEditText.getEditText().setOnEditorActionListener(this);
        mEditText.setHint(getString(R.string.open_mrl_dialog_msg));
        mRecyclerView = (RecyclerView) v.findViewById(R.id.mrl_list);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(v.getContext(), DividerItemDecoration.VERTICAL_LIST));
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new MRLAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mSend.setOnClickListener(this);
        return v;
    }

    public void onStart(){
        super.onStart();
        getActivity().supportInvalidateOptionsMenu();
        updateHistory();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mEditText != null && mEditText.getEditText() != null)
            outState.putString(KEY_MRL, mEditText.getEditText().getText().toString());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState == null || mEditText == null)
            return;
        String mrl = savedInstanceState.getString(KEY_MRL);
        if (mEditText != null && mEditText.getEditText() != null)
            mEditText.getEditText().setText(mrl);
    }

    private void updateHistory() {
        mAdapter.setList(VLCApplication.getMLInstance().lastStreamsPlayed());
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return (keyCode == EditorInfo.IME_ACTION_DONE ||
            keyCode == EditorInfo.IME_ACTION_GO ||
                (event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                && processUri();
    }

    private boolean processUri() {
        if (mEditText.getEditText() != null && !TextUtils.isEmpty(mEditText.getEditText().getText())) {
            UiTools.setKeyboardVisibility(mEditText, false);
            MediaWrapper mw = new MediaWrapper(Uri.parse(mEditText.getEditText().getText().toString().trim()));
            mw.setType(MediaWrapper.TYPE_STREAM);
            MediaUtils.openMedia(getActivity(), mw);
            updateHistory();
            getActivity().supportInvalidateOptionsMenu();
            mEditText.getEditText().getText().clear();
            return true;
        }
        return false;
    }

    public void clearHistory(){
        VLCApplication.getMLInstance().clearHistory();
        mAdapter.setList(new HistoryItem[0]);
        getActivity().supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

        return false;
    }

    public boolean isEmpty(){
        return mAdapter.isEmpty();
    }

    @Override
    public void onClick(View v) {
        processUri();
    }
}
