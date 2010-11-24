package vlc.android;

import android.app.Activity;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;

import android.view.View;

import java.lang.*;

public class vlc extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final TextView tv = (TextView)findViewById(R.id.text_view);
        final Button button = (Button)findViewById(R.id.button);

        // Create the libVLC instance
        libvlc = new libVLC();

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

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	libvlc.readMedia();
            }
        });
    }
    
    libVLC libvlc;
}
