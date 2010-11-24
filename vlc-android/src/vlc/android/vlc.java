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

        // Create the libVLC instance
        libVLC libvlc = new libVLC();

        if(libvlc.Init())
            tv.setText("Loaded libVLC:\n* version:   " + libvlc.version() +
                                     "\n* compiler:  " + libvlc.compiler() +
                                     "\n* changeset: " + libvlc.changeset() +
                                     "\n* libvlccore loaded\n");
        else
            tv.setText("Loaded libVLC:\n* version:   " + libvlc.version() +
                                     "\n* compiler:  " + libvlc.compiler() +
                                     "\n* changeset: " + libvlc.changeset() +
                                     "\n* libvlccore failed!!!\n");

        setContentView(tv);
    }
}
