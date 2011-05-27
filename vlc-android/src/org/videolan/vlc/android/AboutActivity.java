package org.videolan.vlc.android;

import android.app.Activity;
import android.os.Bundle;

public class AboutActivity extends Activity {
	
	public final static String TAG = "VLC/AboutActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.about);
		super.onCreate(savedInstanceState);
	}

}
