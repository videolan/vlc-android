package vlc.android;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class VlcPreferences extends PreferenceActivity {
    private static final String TAG = "LibVLC/PreferenceActivity";
    
    public static final String ORIENTATION_MODE = "orientation mode";
    public static final String HUD_LOCK         = "hud lock"; 

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(VlcPreferences.ORIENTATION_MODE, true)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        */

        addPreferencesFromResource(R.xml.configuration);
    }
    
    // Convenience function, used here only for debugging
    public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference) {
        Log.v(TAG, "Clicked preference " + preference);
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

}
