package vlc.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.lang.*;

public class vlc extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);

        try
        {
        	System.loadLibrary("vlccore");
    		System.loadLibrary("vlc");
    		System.loadLibrary("vlcjni");
    		
            tv.setText("Loaded libVLC version:" + getLibvlcVersion());
        }
        catch (UnsatisfiedLinkError e)
        {
        	tv.setText("Couldn't load libVLC. :-(");
        }

        setContentView(tv);
    }
    
    public native String  getLibvlcVersion();
}