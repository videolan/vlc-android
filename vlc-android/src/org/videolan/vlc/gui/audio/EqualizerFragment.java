/*****************************************************************************
 * EqualizerFragment.java
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
package org.videolan.vlc.gui.audio;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.gui.view.EqualizerBar;

public class EqualizerFragment extends PlaybackServiceFragment {

    public final static String TAG = "VLC/EqualizerFragment";
    private SwitchCompat button;
    private Spinner equalizer_presets;
    private SeekBar preamp;
    private LinearLayout bands_layout;
    private MediaPlayer.Equalizer mEqualizer = null;
    private static final int BAND_COUNT = MediaPlayer.Equalizer.getBandCount();

    /* All subclasses of Fragment must include a public empty constructor. */
    public EqualizerFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getResources().getString(R.string.equalizer));

        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.equalizer, container, false);
        saveViewChildren(v);

        return v;
    }

    private void saveViewChildren(View v) {
        button = (SwitchCompat) v.findViewById(R.id.equalizer_button);
        equalizer_presets = (Spinner) v.findViewById(R.id.equalizer_presets);
        preamp = (SeekBar) v.findViewById(R.id.equalizer_preamp);
        bands_layout = (LinearLayout) v.findViewById(R.id.equalizer_bands);
    }


    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
    }

    private void fillViews() {
        final Context context = getActivity();

        if (context == null)
            return;

        final String[] presets = getEqualizerPresets();

        mEqualizer = VLCOptions.getEqualizer(context);
        final boolean isEnabled = mEqualizer != null;
        if (mEqualizer == null)
            mEqualizer = MediaPlayer.Equalizer.create();

        // on/off
        button.setChecked(isEnabled);
        button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null)
                    mService.setEqualizer(isChecked ? mEqualizer : null);
            }
        });

        // presets
        equalizer_presets.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item, presets));

        // Set the default selection asynchronously to prevent a layout initialization bug.
        final int equalizer_preset_pref = VLCOptions.getEqualizerPreset(context);
        equalizer_presets.post(new Runnable() {
            @Override
            public void run() {
                equalizer_presets.setSelection(equalizer_preset_pref, false);
                equalizer_presets.setOnItemSelectedListener(mPresetListener);
            }
        });

        // preamp
        preamp.setMax(40);
        preamp.setProgress((int) mEqualizer.getPreAmp() + 20);
        preamp.setOnSeekBarChangeListener(mPreampListener);

        // bands
        for (int i = 0; i < BAND_COUNT; i++) {
            float band = MediaPlayer.Equalizer.getBandFrequency(i);

            EqualizerBar bar = new EqualizerBar(getActivity(), band);
            bar.setValue(mEqualizer.getAmp(i));
            bar.setListener(new BandListener(i));

            bands_layout.addView(bar);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                                                  LayoutParams.MATCH_PARENT, 1);
            bar.setLayoutParams(params);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        button.setOnCheckedChangeListener(null);
        equalizer_presets.setOnItemSelectedListener(null);
        preamp.setOnSeekBarChangeListener(null);
        bands_layout.removeAllViews();

        if (button.isChecked())
            VLCOptions.setEqualizer(getActivity(), mEqualizer, equalizer_presets.getSelectedItemPosition());
        else
            VLCOptions.setEqualizer(getActivity(), null, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        fillViews();
    }

    private final OnItemSelectedListener mPresetListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (mService == null)
                return;

            mEqualizer = MediaPlayer.Equalizer.createFromPreset(pos);

            preamp.setProgress((int) mEqualizer.getPreAmp() + 20);
            for (int i = 0; i < BAND_COUNT; ++i) {
                EqualizerBar bar = (EqualizerBar) bands_layout.getChildAt(i);
                bar.setValue(mEqualizer.getAmp(i));
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    private final OnSeekBarChangeListener mPreampListener = new OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser || mService == null)
                return;

            mEqualizer.setPreAmp(progress - 20);
            if (button.isChecked())
                mService.setEqualizer(mEqualizer);
        }
    };

    private class BandListener implements OnEqualizerBarChangeListener {
        private int index;

        public BandListener(int index) {
            this.index = index;
        }

        @Override
        public void onProgressChanged(float value) {
            mEqualizer.setAmp(index, value);
            if (button.isChecked() && mService != null)
                mService.setEqualizer(mEqualizer);
        }
    }

    private static String[] getEqualizerPresets() {
        final int count = MediaPlayer.Equalizer.getPresetCount();
        if (count <= 0)
            return null;
        final String [] presets = new String[count];
        for (int i = 0; i < count; ++i) {
            presets[i] = MediaPlayer.Equalizer.getPresetName(i);
        }
        return presets;
    }
}