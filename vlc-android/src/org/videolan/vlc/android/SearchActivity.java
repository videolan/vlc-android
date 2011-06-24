package org.videolan.vlc.android;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

public class SearchActivity extends ListActivity {
	public final static String TAG = "VLC/SearchActivit";
	
	private EditText mSearchText;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);
        
        mSearchText = (EditText)findViewById(R.id.search_text);
        
        final Intent queryIntent = getIntent();
		final String queryAction = queryIntent.getAction();
		if (Intent.ACTION_SEARCH.equals(queryAction)) {
			mSearchText.setText(queryIntent.getStringExtra(SearchManager.QUERY));
			mSearchText.requestFocus();
		}
        
    }

}
