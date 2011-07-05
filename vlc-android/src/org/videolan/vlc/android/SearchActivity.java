package org.videolan.vlc.android;

import java.util.ArrayList;

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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class SearchActivity extends ListActivity {
	public final static String TAG = "VLC/SearchActivit";
	
	private EditText mSearchText;
	private ArrayAdapter<String> mHistoryAdapter;
	private ArrayList<String> mHistory = new ArrayList<String>();
	private SearchResultAdapter mResultAdapter;
	private LinearLayout mListHeader;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);
        
        // TODO: create layout
        mResultAdapter = new SearchResultAdapter(this, android.R.layout.simple_list_item_1);
        
        mSearchText = (EditText)findViewById(R.id.search_text);
        mSearchText.setOnEditorActionListener(searchTextListener);
        mSearchText.addTextChangedListener(searchTextWatcher);		
        
        final Intent queryIntent = getIntent();
		final String queryAction = queryIntent.getAction();
		if (Intent.ACTION_SEARCH.equals(queryAction)) {
			String query = queryIntent.getStringExtra(SearchManager.QUERY);
			mSearchText.setText(query);
			mSearchText.setSelection(query.length());
		} else {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	    	imm.showSoftInput(mSearchText, InputMethodManager.RESULT_SHOWN);
	    	showSearchHistory();
		}
		
		
		mSearchText.requestFocus();

    }
    
    private void search(CharSequence key, int type) {
    	
    	// set result adapter to the list 
		mResultAdapter.clear();
		String[] keys = key.toString().split("\\s+");
		ArrayList<MediaItem> allItems = MediaLibraryActivity.getInstance().mItemList;
		int results = 0;
		for (int i = 0; i < allItems.size(); i++) {
			MediaItem item = allItems.get(i);
			if (type != MediaItem.TYPE_ALL && type != item.getType())
				continue;
			boolean add = true;
			String name = item.getName().toLowerCase();
			String path = item.getPath().toLowerCase();
			for (int k = 0; k < keys.length; k++) {
				if (!(name.contains(keys[k].toLowerCase()) ||
						path.contains(keys[k].toLowerCase()))) {
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
		
		String headerText = getString(R.string.search_found_results, results);
    	showListHeader(headerText);
		
    	setListAdapter(mResultAdapter);
    }
    
    
    private void showListHeader(String text) {
    	ListView lv = getListView();
    	
    	// create a new header if not exists
    	if (mListHeader == null) {
    		LayoutInflater infalter = getLayoutInflater();
        	mListHeader = (LinearLayout) infalter.inflate(R.layout.list_header, lv, false);
        	lv.addHeaderView(mListHeader, null, false);
    	} 
    	
    	// set header text
    	TextView headerText = (TextView)mListHeader.findViewById(R.id.list_header_text);
    	headerText.setText(text);
    }

    
    private void showSearchHistory() {

    	// Add header to the history
    	String headerText = getString(R.string.search_history);
    	showListHeader(headerText);
    	
    	DatabaseManager db = DatabaseManager.getInstance();
    	mHistory.clear();
    	mHistory.addAll(db.getSearchhistory(20)); 
    	if (mHistoryAdapter == null) {
    		mHistoryAdapter = new ArrayAdapter<String>(this, 
    				android.R.layout.simple_list_item_1, mHistory);
    	} else {
    		mHistoryAdapter.notifyDataSetChanged();
    	}
    	setListAdapter(mHistoryAdapter);
    }
    
    private TextWatcher searchTextWatcher = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			if (s.length() > 0) {
				search(s, MediaItem.TYPE_ALL);
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
			DatabaseManager db = DatabaseManager.getInstance();
			db.clearSearchhistory();
			if (mHistoryAdapter == getListAdapter())
				showSearchHistory();
		}
		return super.onOptionsItemSelected(item);
	}
	
    
    private OnEditorActionListener searchTextListener = new OnEditorActionListener() {	
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	    	imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			return false;
		}
	};
	
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if (getListAdapter() == mHistoryAdapter) {
			String selection = ((TextView)v.findViewById(android.R.id.text1)).getText().toString();
			mSearchText.setText(selection);
			mSearchText.setSelection(selection.length());
			mSearchText.requestFocus();
		} else if (getListAdapter() == mResultAdapter) {
			// add search text to the database (history)
			DatabaseManager db = DatabaseManager.getInstance();
			db.addSearchhistoryItem(mSearchText.getText().toString());
			
			// open media in the player
			MediaItem item = (MediaItem) getListAdapter().getItem(position - 1);
			Intent intent = new Intent(this, VideoPlayerActivity.class);
			intent.putExtra("filePath", item.getPath());
			startActivity(intent);
			super.onListItemClick(l, v, position, id);
			
		}
	};
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH) {
			mSearchText.requestFocus();
			mSearchText.setSelection(mSearchText.getText().length());
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	    	imm.showSoftInput(mSearchText, InputMethodManager.RESULT_SHOWN);
	        return true;
	    }

		return super.onKeyDown(keyCode, event);
	}
    
    

}
