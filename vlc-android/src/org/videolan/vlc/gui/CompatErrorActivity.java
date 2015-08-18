package org.videolan.vlc.gui;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.vlc.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompatErrorActivity extends Activity {
    public final static String TAG = "VLC/CompatErrorActivity";

    /**
     * Simple friendly activity to tell the user something's wrong.
     *
     * Intent parameters (all optional):
     * runtimeError (bool) - Set to true if you want to show a runtime error
     *                       (defaults to a compatibility error)
     * message (string) - the more detailed problem
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.not_compatible);

        String errorMsg = VLCUtil.getErrorMsg();
        if(getIntent().getBooleanExtra("runtimeError", false))
            if(getIntent().getStringExtra("message") != null) {
                errorMsg = getIntent().getStringExtra("message");
                TextView tvo = (TextView)findViewById(R.id.message);
                tvo.setText(R.string.error_problem);
            }

        TextView tv = (TextView)findViewById(R.id.errormsg);
        tv.setText(getResources().getString(R.string.error_message_is) + "\n" + errorMsg);
    }
}
