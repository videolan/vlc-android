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
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.EqualizerBar;
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener;
import org.videolan.vlc.util.VLCOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EqualizerFragment extends AppCompatDialogFragment implements PlaybackService.Client.Callback {
    private PlaybackService mService;

    public final static String TAG = "VLC/EqualizerFragment";
    private SwitchCompat button;

    private ImageView save;
    private ImageView delete;
    private ImageView revert;
    private Spinner equalizer_sets;
    private SeekBar preamp;
    private LinearLayout bands_layout;
    private MediaPlayer.Equalizer mEqualizer = null;
    private static final int BAND_COUNT = MediaPlayer.Equalizer.getBandCount();
    private int customCount = 0;
    private int presetCount = 0;
    private List<String> allSets = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Context context;
    private View dialogView;
    private int revertPos = 0;
    private int savePos = 0;
    private boolean saved = true;
    private boolean updateAlreadyHandled = false;
    private final static int TYPE_PRESET = 0;
    private final static int TYPE_CUSTOM = 1;
    private final static int TYPE_NEW = 2;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.equalizer, container, false);
        saveViewChildren(v);

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        PlaybackServiceFragment.registerPlaybackService(this, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PlaybackServiceFragment.unregisterPlaybackService(this, this);
    }

    private void saveViewChildren(View v) {
        dialogView = v;
        button = v.findViewById(R.id.equalizer_button);
        equalizer_sets = v.findViewById(R.id.equalizer_presets);
        preamp = v.findViewById(R.id.equalizer_preamp);
        bands_layout = v.findViewById(R.id.equalizer_bands);
        save = v.findViewById(R.id.equalizer_save);
        delete = v.findViewById(R.id.equalizer_delete);
        revert = v.findViewById(R.id.equalizer_revert);
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    private void fillViews() {
        context = getActivity();

        if (context == null)
            return;

        allSets.clear();
        allSets = new ArrayList<>();
        allSets.addAll(Arrays.asList(getEqualizerPresets()));
        presetCount = allSets.size();
        for (Map.Entry<String, ?> entry : PreferenceManager.getDefaultSharedPreferences(context).getAll().entrySet()) {
            if (entry.getKey().startsWith("custom_equalizer_")) {
                allSets.add(entry.getKey().replace("custom_equalizer_", "").replace("_", " "));
                customCount++;
            }
        }
        allSets.add("new");

        mEqualizer = VLCOptions.getEqualizerSetFromSettings(context);

        // on/off
        button.setChecked(VLCOptions.getEqualizerEnabledState(context));
        button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null)
                    if (isChecked)
                        mService.setEqualizer(mEqualizer);
                    else
                        mService.setEqualizer(null);
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createSaveCustomSetDialog(equalizer_sets.getSelectedItemPosition(), true, false);
            }
        });
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createDeleteCustomSetSnacker();
            }
        });
        revert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                revertCustomSetChanges();
            }
        });

        // presets
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, allSets);
        equalizer_sets.setAdapter(adapter);

        // Set the default selection asynchronously to prevent a layout initialization bug.
        equalizer_sets.post(new Runnable() {
            @Override
            public void run() {
                equalizer_sets.setOnItemSelectedListener(mSetListener);
                saved = VLCOptions.getEqualizerSavedState(context);
                updateAlreadyHandled = true;
                if (button.isChecked() || !saved) {
                    final int pos = allSets.indexOf(VLCOptions.getEqualizerNameFromSettings(context));
                    savePos = pos;
                    revertPos = getEqualizerType(pos) == TYPE_CUSTOM ? pos : 0;
                    equalizer_sets.setSelection(pos);
                } else {
                    updateEqualizer(0);
                }

            }
        });

        // preamp
        preamp.setMax(40);
        preamp.setProgress((int) mEqualizer.getPreAmp() + 20);
        preamp.setOnSeekBarChangeListener(mPreampListener);

        // bands
        for (int i = 0; i < BAND_COUNT; i++) {
            float band = MediaPlayer.Equalizer.getBandFrequency(i);

            EqualizerBar bar = new EqualizerBar(context, band);
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
        equalizer_sets.setOnItemSelectedListener(null);
        preamp.setOnSeekBarChangeListener(null);
        bands_layout.removeAllViews();
        if (button.isChecked()) {
            int pos = equalizer_sets.getSelectedItemPosition();
            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(pos), true, saved);
        } else {
            VLCOptions.saveEqualizerInSettings(context, MediaPlayer.Equalizer.createFromPreset(0), allSets.get(0), false, true);
        }
        if (!saved)
            createSaveCustomSetDialog(equalizer_sets.getSelectedItemPosition(), false, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        fillViews();
    }

    private final OnItemSelectedListener mSetListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (mService == null)
                return;
            if (!button.isChecked() && !updateAlreadyHandled)
                button.setChecked(true);

            //save set if changes made (needs old currentPosition)
            if (!saved && !updateAlreadyHandled)
                createSaveCustomSetDialog(savePos, false, false);

            updateEqualizer(pos);
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
            if (!button.isChecked())
                button.setChecked(true);

            int pos = equalizer_sets.getSelectedItemPosition();
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos;
                savePos = presetCount + customCount;
                saved = false;
                updateAlreadyHandled = true;
                equalizer_sets.setSelection(presetCount + customCount);
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos;
                savePos = pos;
                saved = false;
                updateButtonVisibility(pos);
            }

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
        public void onProgressChanged(float value, boolean fromUser) {
            if (!fromUser)
                return;
            mEqualizer.setAmp(index, value);
            if (!button.isChecked())
                button.setChecked(true);

            int pos = equalizer_sets.getSelectedItemPosition();
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos;
                savePos = presetCount + customCount;
                saved = false;
                updateAlreadyHandled = true;
                equalizer_sets.setSelection(presetCount + customCount);
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos;
                savePos = pos;
                saved = false;
                updateButtonVisibility(pos);
            }

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

    public void createSaveCustomSetDialog(final int positionToSave, final boolean displayedByUser, final boolean onPause) {
        final String oldName = allSets.get(positionToSave);

        final MediaPlayer.Equalizer temporarySet = MediaPlayer.Equalizer.create();
        temporarySet.setPreAmp(mEqualizer.getPreAmp());
        for (int i=0; i< MediaPlayer.Equalizer.getBandCount(); i++)
            temporarySet.setAmp(i, mEqualizer.getAmp(i));

        final EditText input = new EditText(context);
        input.setText(oldName);
        input.setSelectAllOnFocus(true);

        final AlertDialog saveEqualizer = new AlertDialog.Builder(context)
                .setTitle(getResources().getString(displayedByUser
                        ? R.string.custom_set_save_title
                        : R.string.custom_set_save_warning))
                .setMessage(getResources().getString((getEqualizerType(positionToSave) == TYPE_CUSTOM)
                        ? R.string.existing_custom_set_save_message
                        : R.string.new_custom_set_save_message))
                .setView(input)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.do_not_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (onPause)
                            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(positionToSave), button.isChecked(), false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (onPause)
                            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(positionToSave), button.isChecked(), false);
                    }
                })
                .create();
        saveEqualizer.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        //HACK to prevent closure
        saveEqualizer.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final Button positiveButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String newName = input.getText().toString();
                        if (newName.contains("_") || TextUtils.equals(newName,"new")) {
                            Toast.makeText(VLCApplication.getAppContext(), VLCApplication.getAppContext().getResources().getString(R.string.custom_set_wrong_input), Toast.LENGTH_SHORT).show();
                        } else if (allSets.contains(newName) && !TextUtils.equals(newName,oldName)) {
                            Toast.makeText(VLCApplication.getAppContext(), VLCApplication.getAppContext().getResources().getString(R.string.custom_set_already_exist), Toast.LENGTH_SHORT).show();
                        } else {
                            VLCOptions.saveCustomSet(context, temporarySet, newName);
                            if (onPause) {
                                if (button.isChecked())
                                    VLCOptions.saveEqualizerInSettings(context, temporarySet, newName, true, true);
                            } else {
                                if (TextUtils.equals(newName,oldName)) {
                                    if (displayedByUser) {
                                        saved = true;
                                        updateButtonVisibility(allSets.indexOf(newName));
                                    }
                                } else {
                                    //insert new item before the one being saved in order to keep position
                                    allSets.add(positionToSave, newName);
                                    customCount++;
                                    if (displayedByUser) {
                                        adapter.notifyDataSetChanged();
                                        saved = true;
                                        updateAlreadyHandled = true;
                                        updateEqualizer(allSets.indexOf(newName));
                                    }
                                }
                            }
                            saveEqualizer.dismiss();
                        }
                    }
                });
            }
        });
        saveEqualizer.show();
    }

    public void createDeleteCustomSetSnacker() {
        final int oldPos = equalizer_sets.getSelectedItemPosition();
        final String oldName = allSets.get(oldPos);
        if (getEqualizerType(oldPos) == TYPE_CUSTOM) {

            final MediaPlayer.Equalizer savedEqualizerSet = VLCOptions.getCustomSet(context, oldName);

            Runnable cancelAction = new Runnable() {
                @Override
                public void run() {
                    VLCOptions.saveCustomSet(context, savedEqualizerSet, oldName);
                    mEqualizer = savedEqualizerSet;
                    allSets.add(oldPos, oldName);
                    customCount++;
                    equalizer_sets.setSelection(oldPos);
                }
            };

            VLCOptions.deleteCustomSet(context, oldName);
            allSets.remove(oldName);
            customCount--;
            saved = true;
            equalizer_sets.setSelection(0);
            String message = context.getString(R.string.custom_set_deleted_message, oldName);
            UiTools.snackerWithCancel(dialogView, message, null, cancelAction);
        }
    }

    public void revertCustomSetChanges() {
        final int pos = equalizer_sets.getSelectedItemPosition();

        final MediaPlayer.Equalizer temporarySet = MediaPlayer.Equalizer.create();
        temporarySet.setPreAmp(mEqualizer.getPreAmp());
        for (int i=0; i< MediaPlayer.Equalizer.getBandCount(); i++)
            temporarySet.setAmp(i, mEqualizer.getAmp(i));

        Runnable cancelAction = new Runnable() {
            @Override
            public void run() {
                saved = false;
                mEqualizer = temporarySet;
                updateAlreadyHandled = true;
                if (pos == revertPos)
                    updateEqualizer(pos);
                else
                    equalizer_sets.setSelection(pos);
            }
        };
        saved = true;
        if (pos == revertPos)
            updateEqualizer(revertPos);
        else
            equalizer_sets.setSelection(revertPos);

        String message = getEqualizerType(pos) == TYPE_CUSTOM
                ? context.getString(R.string.custom_set_restored)
                : context.getString(R.string.unsaved_set_deleted_message);
        UiTools.snackerWithCancel(dialogView, message, null, cancelAction);
    }

    private void updateEqualizer(int pos) {
        if (updateAlreadyHandled) {
            updateAlreadyHandled = false;
        } else {
            if (getEqualizerType(pos) == TYPE_PRESET) {
                mEqualizer = MediaPlayer.Equalizer.createFromPreset(pos);
                saved = true;
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                mEqualizer = VLCOptions.getCustomSet(context, allSets.get(pos));
                saved = true;
            } else if (getEqualizerType(pos) == TYPE_NEW) {
                mEqualizer = MediaPlayer.Equalizer.create();
                saved = false;
            } else {
                saved = false;
            }
        }

        updateButtonVisibility(pos);

        preamp.setProgress((int) mEqualizer.getPreAmp() + 20);
        for (int i = 0; i < BAND_COUNT; ++i) {
            EqualizerBar bar = (EqualizerBar) bands_layout.getChildAt(i);
            if (bar != null)
                bar.setValue(mEqualizer.getAmp(i));
        }
        if (button.isChecked())
            mService.setEqualizer(mEqualizer);
    }

    public void updateButtonVisibility(int pos) {
        setButtonVisibility(!saved, !saved, (saved && getEqualizerType(pos) == TYPE_CUSTOM));
    }

    private void setButtonVisibility(boolean saveButtonVisible, boolean revertButtonVisible, boolean deleteButtonVisible) {
        save.setVisibility(saveButtonVisible ? View.VISIBLE : View.GONE);
        revert.setVisibility(revertButtonVisible ? View.VISIBLE : View.GONE);
        delete.setVisibility(deleteButtonVisible ? View.VISIBLE : View.GONE);
    }

    private int getEqualizerType(int position) {
        if (position < 0)
            return -1;
        if (position < presetCount)
            return TYPE_PRESET;
        if (position < presetCount + customCount)
            return TYPE_CUSTOM;
        return TYPE_NEW;
    }
}