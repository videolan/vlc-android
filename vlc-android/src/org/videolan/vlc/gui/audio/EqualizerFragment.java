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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener;
import org.videolan.vlc.widget.EqualizerBar;

import com.actionbarsherlock.app.SherlockFragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
import android.widget.ToggleButton;

public class EqualizerFragment extends SherlockFragment {

    public final static String TAG = "VLC/EqualizerFragment";
    private ToggleButton button;
    private Spinner equalizer_presets;
    private SeekBar preamp;
    private LinearLayout bands_layout;
    LibVLC libVlc = null;
    float[] equalizer = null;

    /* All subclasses of Fragment must include a public empty constructor. */
    public EqualizerFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getSherlockActivity().getSupportActionBar().setTitle(getResources().getString(R.string.equalizer));

        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.equalizer, container, false);
        saveViewChildren(v);

        return v;
    }

    private void saveViewChildren(View v) {
        button = (ToggleButton) v.findViewById(R.id.equalizer_button);
        equalizer_presets = (Spinner) v.findViewById(R.id.equalizer_presets);
        preamp = (SeekBar) v.findViewById(R.id.equalizer_preamp);
        bands_layout = (LinearLayout) v.findViewById(R.id.equalizer_bands);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.equalizer, null);
        ViewGroup rootView = (ViewGroup) getView();
        rootView.removeAllViews();
        rootView.addView(v);
        saveViewChildren(v);

        fillViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        fillViews();
    }

    private void fillViews() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        float[] bands = null;
        String[] presets = null;
        try {
            libVlc = Util.getLibVlcInstance();
            bands = libVlc.getBands();
            presets = libVlc.getPresets();
            if (equalizer == null)
                equalizer = Util.getFloatArray(preferences, "equalizer_values");
            if (equalizer == null)
                equalizer = new float[bands.length + 1];
        } catch (LibVlcException e) {
            e.printStackTrace();
            return;
        }

        // on/off
        button.setChecked(libVlc.getEqualizer() != null);
        button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (libVlc == null)
                    return;
                libVlc.setEqualizer(isChecked ? equalizer : null);
            }
        });

        // presets
        equalizer_presets.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item, presets));

        // Set the default selection asynchronously to prevent a layout initialization bug.
        final int equalizer_preset_pref = preferences.getInt("equalizer_preset", 0);
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

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("equalizer_enabled", button.isChecked());
        Util.putFloatArray(editor, "equalizer_values", equalizer);
        editor.putInt("equalizer_preset", equalizer_presets.getSelectedItemPosition());
        editor.commit();
    }

    private final OnItemSelectedListener mPresetListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (libVlc == null)
                return;
            float[] preset = libVlc.getPreset(pos);
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
            if (!fromUser)
                return;

            equalizer[0] = progress - 20;
            if (libVlc != null && button.isChecked())
                libVlc.setEqualizer(equalizer);
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
            if (libVlc != null && button.isChecked())
                libVlc.setEqualizer(equalizer);
        }
    };
}