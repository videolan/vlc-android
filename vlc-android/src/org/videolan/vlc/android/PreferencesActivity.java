package org.videolan.vlc.android;


import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

public class PreferencesActivity extends PreferenceActivity {

	public final static String TAG = "VLC/PreferencesActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		addPreferencesFromResource(R.xml.preferences);
		
		// Create onClickListen
		Preference directoriesPref = (Preference) findPreference("directories");
		directoriesPref.setOnPreferenceClickListener(
				new OnPreferenceClickListener() {
			
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(MediaLibraryActivity.getInstance(), 
						BrowserActivity.class);
				startActivity(intent);
				return true;
			}
		});
	}
	
	



	
	
	
}
