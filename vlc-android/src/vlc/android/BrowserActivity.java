package vlc.android;

import java.io.File;
import java.io.FileFilter;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;

public class BrowserActivity extends ListActivity {
	public final static String TAG = "VLC/BrowserActivity";
	
	/**
	 * TODO:
	 * + save previous scroll state
	 */
	
	private BrowserAdapter mAdapter;
	private File mCurrentDir;
	
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
		
		
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = mAdapter.getItem(position);
		File[] files = file.listFiles(new DirFilter());
		if (files != null && files.length > 0) {
			openDir(file);
		} else {
			Util.toaster("No Subdirectory");
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	if (mCurrentDir.getPath().equals("/")) {
	    		return super.onKeyDown(keyCode, event);
	    	} else {
	    		openDir(mCurrentDir.getParentFile());
	    		return true;
	    	}
	    }
	    return super.onKeyDown(keyCode, event);
	}


	@Override
	protected void onStop() {
		// Update the MediaList
		MediaLibraryActivity.getInstance().updateMediaList();
		super.onStop();
	}



	/** 
	 * Filter: accept only directories
	 */
    private class DirFilter implements FileFilter {

		public boolean accept(File f) {
			return f.isDirectory();
		}   	
    }
	
}
