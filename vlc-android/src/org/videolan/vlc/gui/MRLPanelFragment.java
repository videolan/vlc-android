/*****************************************************************************
 * MRLPanelFragment.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
package org.videolan.vlc.gui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;

public class MRLPanelFragment extends Fragment implements View.OnKeyListener, TextView.OnEditorActionListener {
    private static final String TAG = "VLC/MrlPanelFragment";
    private RecyclerView mRecyclerView;
    private MRLAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    ArrayList<String> mHistory;
    EditText mEditText;

    public MRLPanelFragment(){};

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHistory = MediaDatabase.getInstance().getMrlhistory();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(R.string.open_mrl_dialog_title);
        View v = inflater.inflate(R.layout.mrl_panel, container, false);
        mEditText = (EditText) v.findViewById(R.id.mrl_edit);
        mEditText.setOnKeyListener(this);
        mEditText.setOnEditorActionListener(this);
        mRecyclerView = (RecyclerView) v.findViewById(R.id.mrl_list);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        mAdapter = new MRLAdapter(mHistory);
        mRecyclerView.setAdapter(mAdapter);

        return v;
    }

    private void updateHistory() {
        mHistory = MediaDatabase.getInstance().getMrlhistory();
        mAdapter.setList(mHistory);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == EditorInfo.IME_ACTION_DONE ||
            keyCode == EditorInfo.IME_ACTION_GO ||
            event.getAction() == KeyEvent.ACTION_DOWN &&
            event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            return processUri();
        }
        return false;
    }

    private boolean processUri() {
        if (!TextUtils.isEmpty(mEditText.getText().toString())){
            Util.openStream(getActivity(), mEditText.getText().toString().trim());
            MediaDatabase.getInstance().addMrlhistoryItem(mEditText.getText().toString().trim());
            updateHistory();
            mEditText.getText().clear();
            return true;
        }
        return false;
    }

    public void clearHistory(){
        MediaDatabase.getInstance().clearMrlHistory();
        updateHistory();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

        return false;
    }
}
