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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener;
import org.videolan.vlc.util.Preferences;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.widget.EqualizerBar;

public class EqualizerFragment extends PlaybackServiceFragment {

    public final static String TAG = "VLC/EqualizerFragment";
    private SwitchCompat button;
    private Spinner equalizer_presets;
    private SeekBar preamp;
    private LinearLayout bands_layout;
    float[] equalizer = null;

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
        fillViews();
    }

    private void fillViews() {
        final Context context = getActivity();

        if (mService == null || context == null)
            return;

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final float[] bands;
        final String[] presets;

        bands = mService.getBands();
        presets = mService.getPresets();
        final float[] equalizerOption = VLCOptions.getEqualizer(context);
        if (equalizer == null)
            equalizer = equalizerOption;
        if (equalizer == null)
            equalizer = new float[bands.length + 1];

        // on/off
        button.setChecked(equalizerOption != null);
        button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null)
                    mService.setEqualizer(isChecked ? equalizer : null);
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
        preamp.setProgress((int) equalizer[0] + 20);
        preamp.setOnSeekBarChangeListener(mPreampListener);

        // bands
        for (int i = 0; i < bands.length; i++) {
            float band = bands[i];

            EqualizerBar bar = new EqualizerBar(getActivity(), band);
            bar.setValue(equalizer[i + 1]);
            bar.setListener(new BandListener(i + 1));

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

        VLCOptions.setEqualizer(getActivity(), button.isChecked(), equalizer, equalizer_presets.getSelectedItemPosition());
    }

    private final OnItemSelectedListener mPresetListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (mService == null)
                return;
            float[] preset = mService.getPreset(pos);
            if (preset == null)
                return;

            equalizer = preset;
            preamp.setProgress((int) equalizer[0] + 20);
            for (int i = 0; i < equalizer.length - 1; ++i) {
                EqualizerBar bar = (EqualizerBar) bands_layout.getChildAt(i);
                bar.setValue(equalizer[i + 1]);
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

            equalizer[0] = progress - 20;
            if (button.isChecked())
                mService.setEqualizer(equalizer);
        }
    };

    private class BandListener implements OnEqualizerBarChangeListener {
        private int index;

        public BandListener(int index) {
            this.index = index;
        }

        @Override
        public void onProgressChanged(float value) {
            equalizer[index] = value;
            if (button.isChecked() && mService != null)
                mService.setEqualizer(equalizer);
        }
    }
}