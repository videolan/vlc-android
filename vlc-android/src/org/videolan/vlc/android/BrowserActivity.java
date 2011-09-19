package org.videolan.vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.util.Stack;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;

public class BrowserActivity extends ListActivity {
	public final static String TAG = "VLC/BrowserActivity";

	/**
	 * TODO:
	 */

	private BrowserAdapter mAdapter;
	private File mCurrentDir;
	private Stack<ScrollState> mScollStates = new Stack<ScrollState>();

	private class ScrollState {
		public ScrollState(int index, int top) {
			this.index = index;
			this.top = top;
		}
		int index;
		int top;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.browser);
		super.onCreate(savedInstanceState);
		mAdapter = new BrowserAdapter(this, R.layout.browser_item);
		setListAdapter(mAdapter);

		openDir(new File("/"));
	}

	private void openDir(File file) {
		mAdapter.clear();
		mCurrentDir = file;
		File[] files = file.listFiles(new DirFilter());
		for (int i = 0; i < files.length; i++) {
			mAdapter.add(files[i]);
		}
		mAdapter.sort();
		// set scroll position to top
		getListView().setSelection(0);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = mAdapter.getItem(position);
		File[] files = file.listFiles(new DirFilter());
		if (files != null && files.length > 0) {
			// store scroll state
			int index = l.getFirstVisiblePosition();
			int top = l.getChildAt(0).getTop();
			mScollStates.push(new ScrollState(index, top));
			openDir(file);
		} else {
			Util.toaster(R.string.nosubdirectory);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	if (mCurrentDir.getPath().equals("/")) {
	    		return super.onKeyDown(keyCode, event);
	    	} else {
	    		openDir(mCurrentDir.getParentFile());
	    		// restore scroll state
	    		ScrollState ss = mScollStates.pop();
	    		getListView().setSelectionFromTop(ss.index, ss.top);
	    		return true;
	    	}
	    }
	    return super.onKeyDown(keyCode, event);
	}


	@Override
	protected void onStop() {
		// Update the MediaList
		MediaLibrary.getInstance(this).loadMediaItems();
		super.onStop();
	}



	/**
	 * Filter: accept only directories
	 */
    private class DirFilter implements FileFilter {

		public boolean accept(File f) {
			return f.isDirectory() && !f.isHidden();
		}
    }

}
