/*****************************************************************************
 * DebugLogActivity.java
 *****************************************************************************
 * Copyright © 2013-2015 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import org.videolan.vlc.DebugLogService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.UiTools;

import java.util.ArrayList;
import java.util.List;

public class DebugLogActivity extends Activity implements DebugLogService.Client.Callback {
    public final static String TAG = "VLC/DebugLogActivity";
    private DebugLogService.Client mClient = null;
    private Button mStartButton = null;
    private Button mStopButton = null;
    private Button mCopyButton = null;
    private Button mClearButton = null;
    private Button mSaveButton = null;
    private ListView mLogView;
    private ArrayList<String> mLogList = null;
    private ArrayAdapter<String> mLogAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug_log);

        mStartButton = (Button)findViewById(R.id.start_log);
        mStopButton = (Button)findViewById(R.id.stop_log);
        mLogView = (ListView) findViewById(R.id.log_list);
        mCopyButton = (Button)findViewById(R.id.copy_to_clipboard);
        mClearButton = (Button)findViewById(R.id.clear_log);
        mSaveButton = (Button)findViewById(R.id.save_to_file);

        mClient = new DebugLogService.Client(this, this);

        mStartButton.setEnabled(false);
        mStopButton.setEnabled(false);
        setOptionsButtonsEnabled(false);

        mStartButton.setOnClickListener(mStartClickListener);
        mStopButton.setOnClickListener(mStopClickListener);
        mClearButton.setOnClickListener(mClearClickListener);
        mSaveButton.setOnClickListener(mSaveClickListener);

        mCopyButton.setOnClickListener(mCopyClickListener);
    }

    @Override
    protected void onDestroy() {
        mClient.release();
        super.onDestroy();
    }

    private View.OnClickListener mStartClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mStartButton.setEnabled(false);
            mStopButton.setEnabled(false);
            mClient.start();
        }
    };

    private View.OnClickListener mStopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mStartButton.setEnabled(false);
            mStopButton.setEnabled(false);
            mClient.stop();
        }
    };

    private void setOptionsButtonsEnabled(boolean enabled) {
        mClearButton.setEnabled(enabled);
        mCopyButton.setEnabled(enabled);
        mSaveButton.setEnabled(enabled);
    }

    private View.OnClickListener mClearClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mClient.clear();
            if (mLogList != null) {
                mLogList.clear();
                mLogAdapter.notifyDataSetChanged();
            }
            setOptionsButtonsEnabled(false);
        }
    };

    private View.OnClickListener mSaveClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mClient.save();
        }
    };

    @SuppressWarnings("deprecation")
    private View.OnClickListener mCopyClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final StringBuffer buffer = new StringBuffer();
            for (String line : mLogList)
                buffer.append(line).append("\n");

            android.text.ClipboardManager clipboard = (android.text.ClipboardManager)VLCApplication.getAppContext().getSystemService(CLIPBOARD_SERVICE);
            clipboard.setText(buffer);

            UiTools.snacker(v.getRootView(), R.string.copied_to_clipboard);
        }
    };

    @Override
    public void onStarted(List<String> logList) {
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        if (logList.size() > 0)
            setOptionsButtonsEnabled(true);
        mLogList = new ArrayList<String>(logList);
        mLogAdapter = new ArrayAdapter<String>(this, R.layout.debug_log_item, mLogList);
        mLogView.setAdapter(mLogAdapter);
        mLogView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        if (mLogList.size() > 0)
            mLogView.setSelection(mLogList.size() - 1);
    }

    @Override
    public void onStopped() {
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
    }

    @Override
    public void onLog(String msg) {
        if (mLogList != null) {
            mLogList.add(msg);
            mLogAdapter.notifyDataSetChanged();
            setOptionsButtonsEnabled(true);
        }
    }

    @Override
    public void onSaved(boolean success, String path) {
        if (success) {
            UiTools.snacker(getWindow().getDecorView().findViewById(android.R.id.content), String.format(
                    VLCApplication.getAppResources().getString(R.string.dump_logcat_success),
                    path));
        } else {
            UiTools.snacker(getWindow().getDecorView().findViewById(android.R.id.content), R.string.dump_logcat_failure);
        }
    }
}
