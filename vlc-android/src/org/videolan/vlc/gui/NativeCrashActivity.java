package org.videolan.vlc.gui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.util.Logcat;
import org.videolan.vlc.util.Util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

public class NativeCrashActivity extends Activity {

    private TextView mCrashLog;
    private Button mRestartButton;
    private Button mSendLog;

    private ProgressDialog mProgressDialog;

    private String mLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.native_crash);

        mCrashLog = (TextView) findViewById(R.id.crash_log);
        mRestartButton = (Button) findViewById(R.id.restart_vlc);
        mRestartButton.setEnabled(false);
        mSendLog = (Button) findViewById(R.id.send_log);
        mSendLog.setEnabled(false);

        mRestartButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.os.Process.killProcess(getIntent().getExtras().getInt("PID"));
                Intent i = new Intent(NativeCrashActivity.this, StartActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        });

        mSendLog.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buildDate = "Build date: " + getString(R.string.build_time);
                String builder = "Builder: "  + getString(R.string.build_host);
                String revision = "Revision: " + getString(R.string.build_revision);
                AsyncHttpRequest asyncHttpRequest = new AsyncHttpRequest();
                asyncHttpRequest.execute(Build.BRAND, Build.MANUFACTURER, Build.PRODUCT, Build.MODEL,
                        Build.DEVICE, Build.VERSION.RELEASE,
                        buildDate, builder, revision, mLog);
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
                log = Logcat.getLogcat();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return log;
        }

        @Override
        protected void onPostExecute(String log) {
            mLog = log;
            mCrashLog.setText(log);
            mRestartButton.setEnabled(true);
            mSendLog.setEnabled(true);
        }
    }

    public class AsyncHttpRequest extends AsyncTask<String, String, Boolean> {

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(NativeCrashActivity.this);
            mProgressDialog.setMessage(NativeCrashActivity.this.getText(R.string.sending_log));
            mProgressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (params[0].length() == 0)
                return false;
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("http://people.videolan.org/~magsoft/vlc-android_crashes/upload_crash_log.php");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoInput(true);
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());

                StringBuilder msgBuilder = new StringBuilder();
                for (int i = 0; i < params.length; ++i) {
                    msgBuilder.append(params[i]);
                    msgBuilder.append("\n");
                }
                byte[] body = compress(msgBuilder.toString());
                urlConnection.setFixedLengthStreamingMode(body.length);
                out.write(body);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
            return true;
        }

        private byte[] compress(String string) throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
            GZIPOutputStream gos = new GZIPOutputStream(os);
            gos.write(string.getBytes());
            Util.close(gos);
            byte[] compressed = os.toByteArray();
            Util.close(os);
            return compressed;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mProgressDialog.cancel();
            mSendLog.setEnabled(false);
        }
    }

}
