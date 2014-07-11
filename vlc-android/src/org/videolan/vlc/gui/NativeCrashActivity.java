package org.videolan.vlc.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Logcat;
import org.videolan.vlc.util.Util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
                Intent i = new Intent(NativeCrashActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        });

        mSendLog.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buildDate = "Build date: " + Util.readAsset("builddate.txt", "Unknown");
                String builder = "Builder: "  + Util.readAsset("builder.txt", "unknown");
                String revision = "Revision: " + Util.readAsset("revision.txt", "Unknown revision");
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
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost("http://people.videolan.org/~magsoft/vlc-android_crashes/upload_crash_log.php");

            try {
                String msg = "";
                for (int i = 0; i < params.length; ++i)
                    msg += params[i] + "\n";
                httpPost.setEntity(new ByteArrayEntity(compress(msg)));
                httpClient.execute(httpPost);
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        private byte[] compress(String string) throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
            GZIPOutputStream gos = new GZIPOutputStream(os);
            gos.write(string.getBytes());
            gos.close();
            byte[] compressed = os.toByteArray();
            os.close();
            return compressed;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mProgressDialog.cancel();
            mSendLog.setEnabled(false);
        }
    }

}
