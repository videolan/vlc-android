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
	private String[] mSearchHistory;
	private ArrayAdapter<String> mHistoryAdapter;
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
		}
		
		mSearchText.requestFocus();
		showSearchHistory();
       
    }
    
    private void search(CharSequence key, int type) {
    	
    	// set result adapter to the list 
		removeListHeader();
		setListAdapter(null);
		mResultAdapter.clear();
    	ArrayList<MediaItem> allItems = MediaLibraryActivity.getInstance().mItemList;
    	for (int i = 0; i < allItems.size(); i++) {
    		MediaItem item = allItems.get(i);
    		if ((type == MediaItem.TYPE_VIDEO || // Only video by now!
    				type == item.getType()) &&
    				item.getName().contains(key) ||
    				item.getPath().contains(key)) {
    			mResultAdapter.add(item);
    		}
    	}
    	mResultAdapter.sort();
    	setListAdapter(mResultAdapter);
    }
    

    
    private void addListHeader(int resid) {
    	ListView lv = getListView();
    	
    	if (mListHeader == null) {
    		LayoutInflater infalter = getLayoutInflater();
        	mListHeader = (LinearLayout) infalter.inflate(R.layout.list_header, lv, false);
    	}
    	
    	TextView headerText = (TextView)mListHeader.findViewById(R.id.list_header_text);
    	headerText.setText(R.string.search_history);
    	getListView().addHeaderView(mListHeader, null, false);
    }
    
    private void removeListHeader() {
    	if (mListHeader != null)
    		getListView().removeHeaderView(mListHeader);
    }
    
    private void showSearchHistory() {
    	setListAdapter(null);
    	// Add header to the history
    	addListHeader(R.string.search_history);
    	
    	if (mSearchHistory == null)
    		loadSearchHistory();
    	if (mHistoryAdapter == null)
    		mHistoryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSearchHistory);
    	setListAdapter(mHistoryAdapter);
    }
    
    private void loadSearchHistory() {
    	DatabaseManager db = DatabaseManager.getInstance();
    	if (db != null)
    	mSearchHistory = db.getSearchhistory(20);
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
			MediaItem item = (MediaItem) getListAdapter().getItem(position);
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
