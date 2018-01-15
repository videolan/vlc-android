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

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaUtils;

public class MRLPanelFragment extends DialogFragment implements View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener, MRLAdapter.MediaPlayerController {
    private static final String TAG = "VLC/MrlPanelFragment";
    public static final String KEY_MRL = "mrl";
    private MRLAdapter mAdapter;
    private TextInputLayout mEditText;

    public MRLPanelFragment(){}

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setStyle(DialogFragment.STYLE_NO_FRAME, 0);
        final View v = inflater.inflate(R.layout.mrl_panel, container, false);
        mEditText = v.findViewById(R.id.mrl_edit);
        mEditText.getEditText().setOnKeyListener(this);
        mEditText.getEditText().setOnEditorActionListener(this);
        mEditText.setHint(getString(R.string.open_mrl_dialog_msg));
        RecyclerView recyclerView = (RecyclerView) v.findViewById(R.id.mrl_list);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MRLAdapter(this);
        recyclerView.setAdapter(mAdapter);
        v.findViewById(R.id.send).setOnClickListener(this);
        return v;
    }

    public void onStart() {
        super.onStart();
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
        if (savedInstanceState == null || mEditText == null) return;
        final String mrl = savedInstanceState.getString(KEY_MRL);
        if (mEditText != null && mEditText.getEditText() != null) mEditText.getEditText().setText(mrl);
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
            final MediaWrapper mw = new MediaWrapper(Uri.parse(mEditText.getEditText().getText().toString().trim()));
            playMedia(mw);
            mEditText.getEditText().getText().clear();
            return true;
        }
        return false;
    }

    public void playMedia(MediaWrapper mw) {
        mw.setType(MediaWrapper.TYPE_STREAM);
        MediaUtils.openMedia(getActivity(), mw);
        updateHistory();
        getActivity().supportInvalidateOptionsMenu();
        UiTools.setKeyboardVisibility(mEditText, false);
        dismiss();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        final Activity activity = getActivity();
        if (activity instanceof DialogActivity) activity.finish();
    }
}
