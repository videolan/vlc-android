/*****************************************************************************
 * SearchActivity.java
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
import java.util.Locale;

import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioPlayerFragment;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class SearchActivity extends ListActivity {

    public final static String TAG = "VLC/SearchActivit";

    private EditText mSearchText;
    private SearchHistoryAdapter mHistoryAdapter;
    private SearchResultAdapter mResultAdapter;
    private LinearLayout mListHeader;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        // TODO: create layout
        mHistoryAdapter = new SearchHistoryAdapter(this);
        mResultAdapter = new SearchResultAdapter(this);

        mSearchText = (EditText) findViewById(R.id.search_text);
        mSearchText.setOnEditorActionListener(searchTextListener);
        mSearchText.addTextChangedListener(searchTextWatcher);

        final Intent queryIntent = getIntent();
        final String queryAction = queryIntent.getAction();
        if (Intent.ACTION_SEARCH.equals(queryAction)) {
            String query = queryIntent.getStringExtra(SearchManager.QUERY);
            mSearchText.setText(query);
            mSearchText.setSelection(query.length());
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchText, InputMethodManager.RESULT_SHOWN);
            showSearchHistory();
        }

        mSearchText.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHistoryAdapter.clear();
        mResultAdapter.clear();
    }

    private void search(CharSequence key, int type) {

        // set result adapter to the list
        mResultAdapter.clear();
        String[] keys = key.toString().split("\\s+");
        ArrayList<Media> allItems = MediaLibrary.getInstance(this).getMediaItems();
        int results = 0;
        for (int i = 0; i < allItems.size(); i++) {
            Media item = allItems.get(i);
            if (type != Media.TYPE_ALL && type != item.getType())
                continue;
            boolean add = true;
            String name = item.getTitle().toLowerCase(Locale.getDefault());
            String MRL = item.getLocation().toLowerCase(Locale.getDefault());
            for (int k = 0; k < keys.length; k++) {
                String s = keys[k].toLowerCase(Locale.getDefault());
                if (!(name.contains(s) || MRL.contains(s))) {
                    add = false;
                    break;
                }
            }

            if (add) {
                mResultAdapter.add(item);
                results++;
            }

        }
        mResultAdapter.sort();

        String headerText = getResources().getQuantityString(R.plurals.search_found_results_quantity, results, results);
        showListHeader(headerText);

        setListAdapter(mResultAdapter);
    }

    private void showListHeader(String text) {
        ListView lv = getListView();

        // Create a new header if it doesn't already exist
        if (mListHeader == null) {
            LayoutInflater infalter = getLayoutInflater();
            mListHeader = (LinearLayout) infalter.inflate(R.layout.list_header, lv, false);
            lv.addHeaderView(mListHeader, null, false);
        }

        // Set header text
        TextView headerText = (TextView) mListHeader.findViewById(R.id.text);
        headerText.setText(text);
    }

    private void showSearchHistory() {

        // Add header to the history
        String headerText = getString(R.string.history);
        showListHeader(headerText);

        MediaDatabase db = MediaDatabase.getInstance(this);
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
                search(s, Media.TYPE_ALL);
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

    /** Create menu from XML
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            // Sort by name
            case R.id.search_clear_history:
                MediaDatabase db = MediaDatabase.getInstance(this);
                db.clearSearchhistory();
                if (mHistoryAdapter == getListAdapter())
                    showSearchHistory();
        }
        return super.onOptionsItemSelected(item);
    }

    private final OnEditorActionListener searchTextListener = new OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            return false;
        }
    };

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (getListAdapter() == mHistoryAdapter) {
            String selection = ((TextView) v.findViewById(android.R.id.text1)).getText().toString();
            mSearchText.setText(selection);
            mSearchText.setSelection(selection.length());
            mSearchText.requestFocus();
        } else if (getListAdapter() == mResultAdapter) {
            // add search text to the database (history)
            MediaDatabase db = MediaDatabase.getInstance(this);
            db.addSearchhistoryItem(mSearchText.getText().toString());

            // open media in the player
            Media item = (Media) getListAdapter().getItem(position - 1);
            if (item.getType() == Media.TYPE_VIDEO) {
                VideoPlayerActivity.start(this, item.getLocation());
            } else {
                ArrayList<String> arr = new ArrayList<String>();
                for (int i = 0; i < getListAdapter().getCount(); i++) {
                    Media audioItem = (Media) getListAdapter().getItem(i);
                    if (audioItem.getType() == Media.TYPE_AUDIO)
                        arr.add(audioItem.getLocation());
                }
                AudioServiceController.getInstance().load(arr, arr.indexOf(item.getLocation()));
                AudioPlayerFragment.start(this);
                finish();
                return;
            }
            super.onListItemClick(l, v, position, id);

        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            mSearchText.requestFocus();
            mSearchText.setSelection(mSearchText.getText().length());
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchText, InputMethodManager.RESULT_SHOWN);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
