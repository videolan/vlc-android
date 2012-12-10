/*****************************************************************************
 * SpeedSelectorDialog.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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

import org.videolan.vlc.LibVLC;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class SpeedSelectorDialog extends Dialog {

    public SpeedSelectorDialog(Context context) {
        super(context);
        setTitle(VLCApplication.getAppContext().getString(R.string.playback_speed));

        LayoutInflater inflator = LayoutInflater.from(context);
        View view = inflator.inflate(R.layout.speed_selector, null);
        setContentView(view);

        final SeekBar seekbar = (SeekBar)findViewById(R.id.speed_seek_bar);
        final TextView speedLabel = (TextView)findViewById(R.id.current_speed);
        Button resetButton = (Button)findViewById(R.id.reset);

        float rate;
        LibVLC libVLC = LibVLC.getExistingInstance();
        if (libVLC != null)
            rate = libVLC.getRate();
        else
            rate = (float) 1.0;

        speedLabel.setText(String.format(java.util.Locale.US, "%.2fx", rate));
        seekbar.setProgress((int) (((Math.log(rate) / Math.log(4)) + 1) * 100));

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                float rate = (float) Math.pow(4, ((double)progress/(double)100) - 1);
                speedLabel.setText(String.format(java.util.Locale.US, "%.2fx", rate));
                LibVLC.getExistingInstance().setRate(rate);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekbar.setProgress(100);
                LibVLC.getExistingInstance().setRate(1);
            }
        });
    }

    /**
     * Return play speed
     */
    public String getSpeedInfo () {
        LibVLC libVLC = LibVLC.getExistingInstance();
        if (libVLC != null)
            return Util.formatRateString(libVLC.getRate());
        else
            return "";
    }
}
