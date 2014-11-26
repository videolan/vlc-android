/*****************************************************************************
 * SearchFragment.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

import java.util.ArrayList;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class SearchFragment extends ListFragment {

    public final static String TAG = "VLC/SearchActivity";

    private EditText mSearchText;
    private SearchHistoryAdapter mHistoryAdapter;
    private SearchResultAdapter mResultAdapter;
    private LinearLayout mListHeader;

    final private Handler mHandler = new Handler();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(R.string.search);

        View v = inflater.inflate(R.layout.search, container, false);

        // TODO: create layout
        mHistoryAdapter = new SearchHistoryAdapter(getActivity());
        mResultAdapter = new SearchResultAdapter(getActivity());

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v = getView();

        mSearchText = (EditText) v.findViewById(R.id.search_text);
        mSearchText.setOnEditorActionListener(searchTextListener);
        mSearchText.addTextChangedListener(searchTextWatcher);
    }

    @Override
    public void onResume() {
        super.onResume();

        mSearchText.requestFocus();

        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mSearchText, InputMethodManager.SHOW_IMPLICIT);

        showSearchHistory();
    }

    @Override
    public void onPause() {
        super.onPause();

        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
    }

    private void search(final String key, final int type) {
        mResultAdapter.clear();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Media> mediaList = MediaLibrary.getInstance().searchMedia(key, type);
                mHandler.post(new Runnable() {
                    public void run() {
                        int count = mediaList.size();
                        for (int i = 0 ; i < count ; ++i)
                            mResultAdapter.add(mediaList.get(i));
                        mResultAdapter.sort();

                        String headerText = getResources().getQuantityString(R.plurals.search_found_results_quantity, mediaList.size(), mediaList.size());
                        showListHeader(headerText);

                        setListAdapter(mResultAdapter);
                    }
                });
            }
        }).start();

    }

    private void showListHeader(String text) {
        ListView lv = getListView();

        // Create a new header if it doesn't already exist
        if (mListHeader == null) {
            LayoutInflater infalter =  (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mListHeader = (LinearLayout) infalter.inflate(R.layout.list_header, lv, false);
            lv.addHeaderView(mListHeader, null, false);
        }

        // Set header text
        TextView headerText = (TextView) mListHeader.findViewById(R.id.text);
        headerText.setText(text);
    }

    private void showSearchHistory() {
        // Add header to the history
        String headerText = getString(R.string.search_history);
        showListHeader(headerText);

        MediaDatabase db = MediaDatabase.getInstance();
        mHistoryAdapter.clear();
        ArrayList<String> history = db.getSearchhistory(20);
        for (String s : history)
            mHistoryAdapter.add(s);
        mHistoryAdapter.notifyDataSetChanged();
        setListAdapter(mHistoryAdapter);
    }

    private final TextWatcher searchTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() > 0) {
                search(s.toString(), Media.TYPE_ALL);
            } else {
                showSearchHistory();
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {

        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    };

    private final OnEditorActionListener searchTextListener = new OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            return false;
        }
    };

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (getListAdapter() == mHistoryAdapter) {
            String selection = ((TextView) v.findViewById(android.R.id.text1)).getText().toString();
            mSearchText.setText(selection);
            mSearchText.setSelection(selection.length());
            mSearchText.requestFocus();
        } else if (getListAdapter() == mResultAdapter) {
            // add search text to the database (history)
            MediaDatabase db = MediaDatabase.getInstance();
            db.addSearchhistoryItem(mSearchText.getText().toString());

            // open media in the player
            Media item = (Media) getListView().getItemAtPosition(position);
            if (item != null) {
                if (item.getType() == Media.TYPE_VIDEO) {
                    VideoPlayerActivity.start(getActivity(), item.getLocation());
                } else {
                    ArrayList<String> arr = new ArrayList<String>();
                    for (int i = 0; i < getListAdapter().getCount(); i++) {
                        Media audioItem = (Media) getListAdapter().getItem(i);
                        if (audioItem.getType() == Media.TYPE_AUDIO)
                            arr.add(audioItem.getLocation());
                    }
                    AudioServiceController.getInstance().load(arr, arr.indexOf(item.getLocation()));
                    return;
                }
            }
            super.onListItemClick(l, v, position, id);

        }
    };

    public void onSearchKeyPressed() {
        if (mSearchText == null)
            return;
        mSearchText.requestFocus();
        mSearchText.setSelection(mSearchText.getText().length());
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mSearchText, InputMethodManager.RESULT_SHOWN);
    }
}
