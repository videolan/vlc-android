/*****************************************************************************
 * DebugLogActivity.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DebugLogActivity extends Activity {
    public final static String TAG = "VLC/DebugLogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug_log);

        final LibVLC instance;
        try {
            instance = Util.getLibVlcInstance();
        } catch (LibVlcException e) { return; }

        final Button startLog = (Button)findViewById(R.id.start_log);
        final Button stopLog = (Button)findViewById(R.id.stop_log);

        startLog.setEnabled(! instance.isDebugBuffering());
        stopLog.setEnabled(instance.isDebugBuffering());

        startLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                instance.startDebugBuffer();
                startLog.setEnabled(false);
                stopLog.setEnabled(true);
            }
        });

        stopLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                instance.stopDebugBuffer();
                stopLog.setEnabled(false);
                startLog.setEnabled(true);
            }
        });

        Button clearLog = (Button)findViewById(R.id.clear_log);
        clearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                instance.clearBuffer();
                updateTextView(instance);
            }
        });

        updateTextView(instance);

        Button copyToClipboard = (Button)findViewById(R.id.copy_to_clipboard);
        if(((TextView)findViewById(R.id.textview)).getText().length() > 0)
            copyToClipboard.setEnabled(true);
            copyToClipboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTextToClipboard();
                Toast.makeText(DebugLogActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void copyTextToClipboard() {
        android.text.ClipboardManager clipboard = (android.text.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(((TextView)findViewById(R.id.textview)).getText());
    }

    private void updateTextView(final LibVLC instance) {
        TextView textView = (TextView)findViewById(R.id.textview);
        textView.setText(instance.getBufferContent());
    }
}
