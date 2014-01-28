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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.HorizontalScrollView;
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

        button = (ToggleButton) v.findViewById(R.id.equalizer_button);
        equalizer_presets = (Spinner) v.findViewById(R.id.equalizer_presets);
        preamp = (SeekBar) v.findViewById(R.id.equalizer_preamp);
        bands_layout = (LinearLayout) v.findViewById(R.id.equalizer_bands);

        // only allow scroll in the lower 50dp part of the layout (where frequencies are displayed)
        HorizontalScrollView scroll = (HorizontalScrollView) v.findViewById(R.id.equalizer_scroll);
        final float density = this.getResources().getDisplayMetrics().density;
        scroll.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int delta = v.getHeight() - (int) event.getY();
                return delta > 50 * density;
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

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
        equalizer_presets.setSelection(preferences.getInt("equalizer_preset", 0), false);
        // set listener asynchronously to prevent the listener from being fired during spinner init
        equalizer_presets.post(new Runnable() {
            @Override
            public void run() {
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
            LayoutParams params = bar.getLayoutParams();
            params.height = LayoutParams.MATCH_PARENT;
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