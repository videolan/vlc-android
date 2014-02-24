/*****************************************************************************
 * SpeedSelector.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.expandable;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.widget.ExpandableLayout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SpeedSelector extends ExpandableLayout {
    public final static String TAG = "VLC/SpeedSelector";

    private final SeekBar mSeekbar;

    public SpeedSelector(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTitle(R.string.playback_speed);
        setIcon(Util.getResourceFromAttribute(context, R.attr.ic_speed_normal_style));
        setContent(context, R.layout.expandable_speed_selector);

        mSeekbar = (SeekBar) findViewById(R.id.speed_seek_bar);
        final Button resetButton = (Button) findViewById(R.id.reset);

        float rate = 1.0f;

        //libvlc is not available in layout editor
        if (!isInEditMode()) {
            LibVLC libVLC = LibVLC.getExistingInstance();
            if (libVLC != null)
                rate = libVLC.getRate();
        }

        setText(Util.formatRateString(rate));
        mSeekbar.setProgress((int) (((Math.log(rate) / Math.log(4)) + 1) * 100));

        mSeekbar.setOnSeekBarChangeListener(mSeekBarListener);
        resetButton.setOnClickListener(mResetListener);
    }

    private OnSeekBarChangeListener mSeekBarListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float rate = (float) Math.pow(4, ((double) progress / (double) 100) - 1);
            setText(Util.formatRateString(rate));
            LibVLC.getExistingInstance().setRate(rate);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private OnClickListener mResetListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mSeekbar.setProgress(100);
            LibVLC.getExistingInstance().setRate(1);
        }
    };
}
