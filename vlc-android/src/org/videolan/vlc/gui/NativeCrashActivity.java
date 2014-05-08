package org.videolan.vlc.gui;

import java.io.IOException;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class NativeCrashActivity extends Activity {

    private TextView mCrashLog;
    private Button mRestartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.native_crash);

        mCrashLog = (TextView) findViewById(R.id.crash_log);
        mRestartButton = (Button) findViewById(R.id.restart_vlc);
        mRestartButton.setEnabled(false);

        mRestartButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(NativeCrashActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        });

        new LogTask().execute();
    }

    class LogTask extends AsyncTask<Void, Void, String>
    {
        @Override
        protected String doInBackground(Void... v) {
            String log = null;
            try {
                log = Util.getLogcat();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return log;
        }

        @Override
        protected void onPostExecute(String log) {
            mCrashLog.setText(log);
            mRestartButton.setEnabled(true);
        }
    }

}
