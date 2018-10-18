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
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableInt;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.EqualizerBinding;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.EqualizerBar;
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.VLCOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EqualizerFragment extends AppCompatDialogFragment implements PlaybackService.Client.Callback {
    private PlaybackService mService;

    public final static String TAG = "VLC/EqualizerFragment";

    private MediaPlayer.Equalizer mEqualizer = null;
    private PlaybackServiceActivity.Helper mHelper;
    private static final int BAND_COUNT = MediaPlayer.Equalizer.getBandCount();
    private int customCount = 0;
    private int presetCount = 0;
    private List<String> allSets = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Context context;
    private int revertPos = 0;
    private int savePos = 0;
    private boolean updateAlreadyHandled = false;
    private EqualizerBinding binding;
    final private EqualizerState mState = new EqualizerState();
    private final String newPresetName = VLCApplication.getAppResources().getString(R.string.equalizer_new_preset_name);

    private final static int TYPE_PRESET = 0;
    private final static int TYPE_CUSTOM = 1;
    private final static int TYPE_NEW = 2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHelper = new PlaybackServiceActivity.Helper(getActivity(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = DataBindingUtil.inflate(inflater, R.layout.equalizer, container, false);
        binding.setState(mState);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        mHelper.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mHelper.onStop();
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
        for (Map.Entry<String, ?> entry : Settings.INSTANCE.getInstance(context).getAll().entrySet()) {
            if (entry.getKey().startsWith("custom_equalizer_")) {
                allSets.add(entry.getKey().replace("custom_equalizer_", "").replace("_", " "));
                customCount++;
            }
        }
        allSets.add(newPresetName);

        mEqualizer = VLCOptions.getEqualizerSetFromSettings(context, true);

        // on/off
        binding.equalizerButton.setChecked(VLCOptions.getEqualizerEnabledState(context));
        binding.equalizerButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null)  mService.setEqualizer(isChecked ? mEqualizer : null);
            }
        });
        binding.equalizerSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createSaveCustomSetDialog(binding.equalizerPresets.getSelectedItemPosition(), true, false);
            }
        });
        binding.equalizerDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createDeleteCustomSetSnacker();
            }
        });
        binding.equalizerRevert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                revertCustomSetChanges();
            }
        });

        // presets
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, allSets);
        binding.equalizerPresets.setAdapter(adapter);

        // Set the default selection asynchronously to prevent a layout initialization bug.
        binding.equalizerPresets.post(new Runnable() {
            @Override
            public void run() {
                binding.equalizerPresets.setOnItemSelectedListener(mSetListener);
                final int pos = allSets.indexOf(VLCOptions.getEqualizerNameFromSettings(context));
                mState.update(pos, VLCOptions.getEqualizerSavedState(context));
                updateAlreadyHandled = true;
                if (binding.equalizerButton.isChecked() || !mState.saved) {
                    savePos = pos;
                    revertPos = getEqualizerType(pos) == TYPE_CUSTOM ? pos : 0;
                    binding.equalizerPresets.setSelection(pos);
                } else {
                    updateEqualizer(0);
                }

            }
        });

        // preamp
        binding.equalizerPreamp.setMax(40);
        binding.equalizerPreamp.setProgress((int) mEqualizer.getPreAmp() + 20);
        binding.equalizerPreamp.setOnSeekBarChangeListener(mPreampListener);

        // bands
        for (int i = 0; i < BAND_COUNT; i++) {
            float band = MediaPlayer.Equalizer.getBandFrequency(i);

            EqualizerBar bar = new EqualizerBar(context, band);
            bar.setValue(mEqualizer.getAmp(i));
            bar.setListener(new BandListener(i));

            binding.equalizerBands.addView(bar);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                                                  LayoutParams.MATCH_PARENT, 1);
            bar.setLayoutParams(params);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.equalizerButton.setOnCheckedChangeListener(null);
        binding.equalizerPresets.setOnItemSelectedListener(null);
        binding.equalizerPreamp.setOnSeekBarChangeListener(null);
        binding.equalizerBands.removeAllViews();
        if (binding.equalizerButton.isChecked()) {
            int pos = binding.equalizerPresets.getSelectedItemPosition();
            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(pos), true, mState.saved);
        } else {
            VLCOptions.saveEqualizerInSettings(context, MediaPlayer.Equalizer.createFromPreset(0), allSets.get(0), false, true);
        }
        if (!mState.saved)
            createSaveCustomSetDialog(binding.equalizerPresets.getSelectedItemPosition(), false, true);
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
            if (!binding.equalizerButton.isChecked() && !updateAlreadyHandled)
                binding.equalizerButton.setChecked(true);

            //save set if changes made (needs old currentPosition)
            if (savePos >= 0 && !mState.saved && !updateAlreadyHandled)
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
            if (!binding.equalizerButton.isChecked())
                binding.equalizerButton.setChecked(true);

            int pos = binding.equalizerPresets.getSelectedItemPosition();
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos;
                savePos = presetCount + customCount;
                mState.update(presetCount + customCount, false);
                updateAlreadyHandled = true;
                binding.equalizerPresets.setSelection(presetCount + customCount);
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos;
                savePos = pos;
                mState.update(pos, false);
            }

            if (binding.equalizerButton.isChecked())
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
            if (!binding.equalizerButton.isChecked())
                binding.equalizerButton.setChecked(true);

            int pos = binding.equalizerPresets.getSelectedItemPosition();
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos;
                savePos = presetCount + customCount;
                mState.update(presetCount + customCount, false);
                updateAlreadyHandled = true;
                binding.equalizerPresets.setSelection(presetCount + customCount);
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos;
                savePos = pos;
                mState.update(pos, false);
            }

            if (binding.equalizerButton.isChecked() && mService != null)
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
                            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(positionToSave), binding.equalizerButton.isChecked(), false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (onPause)
                            VLCOptions.saveEqualizerInSettings(context, mEqualizer, allSets.get(positionToSave), binding.equalizerButton.isChecked(), false);
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
                        final String newName = input.getText().toString();
                        if (newName.contains("_") || TextUtils.equals(newName, newPresetName)) {
                            Toast.makeText(context, VLCApplication.getAppContext().getResources().getString(R.string.custom_set_wrong_input), Toast.LENGTH_SHORT).show();
                        } else if (allSets.contains(newName) && !TextUtils.equals(newName,oldName)) {
                            Toast.makeText(context, VLCApplication.getAppContext().getResources().getString(R.string.custom_set_already_exist), Toast.LENGTH_SHORT).show();
                        } else {
                            VLCOptions.saveCustomSet(context, temporarySet, newName);
                            if (onPause) {
                                if (binding.equalizerButton.isChecked())
                                    VLCOptions.saveEqualizerInSettings(context, temporarySet, newName, true, true);
                            } else {
                                if (TextUtils.equals(newName,oldName)) {
                                    if (displayedByUser) {
                                        mState.update(allSets.indexOf(newName), true);
                                    }
                                } else {
                                    //insert new item before the one being saved in order to keep position
                                    allSets.add(positionToSave, newName);
                                    customCount++;
                                    if (displayedByUser) {
                                        adapter.notifyDataSetChanged();
                                        mState.update(allSets.indexOf(newName), true);
                                        updateAlreadyHandled = true;
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
        final int oldPos = binding.equalizerPresets.getSelectedItemPosition();
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
                    binding.equalizerPresets.setSelection(oldPos);
                }
            };

            VLCOptions.deleteCustomSet(context, oldName);
            allSets.remove(oldName);
            customCount--;
            mState.update(0, true);
            binding.equalizerPresets.setSelection(0);
            String message = context.getString(R.string.custom_set_deleted_message, oldName);
            UiTools.snackerWithCancel(binding.getRoot(), message, null, cancelAction);
        }
    }

    public void revertCustomSetChanges() {
        final int pos = binding.equalizerPresets.getSelectedItemPosition();

        final MediaPlayer.Equalizer temporarySet = MediaPlayer.Equalizer.create();
        temporarySet.setPreAmp(mEqualizer.getPreAmp());
        for (int i=0; i< MediaPlayer.Equalizer.getBandCount(); i++)
            temporarySet.setAmp(i, mEqualizer.getAmp(i));

        Runnable cancelAction = new Runnable() {
            @Override
            public void run() {
                mState.update(pos, false);
                mEqualizer = temporarySet;
                updateAlreadyHandled = true;
                if (pos == revertPos)
                    updateEqualizer(pos);
                else
                    binding.equalizerPresets.setSelection(pos);
            }
        };
        mState.update(revertPos, true);
        if (pos == revertPos)
            updateEqualizer(revertPos);
        else
            binding.equalizerPresets.setSelection(revertPos);

        String message = getEqualizerType(pos) == TYPE_CUSTOM
                ? context.getString(R.string.custom_set_restored)
                : context.getString(R.string.unsaved_set_deleted_message);
        UiTools.snackerWithCancel(binding.getRoot(), message, null, cancelAction);
    }

    private void updateEqualizer(int pos) {
        if (updateAlreadyHandled) {
            updateAlreadyHandled = false;
        } else {
            if (getEqualizerType(pos) == TYPE_PRESET) {
                mEqualizer = MediaPlayer.Equalizer.createFromPreset(pos);
                mState.update(pos, true);
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                mEqualizer = VLCOptions.getCustomSet(context, allSets.get(pos));
                mState.update(pos, true);
            } else if (getEqualizerType(pos) == TYPE_NEW) {
                mEqualizer = MediaPlayer.Equalizer.create();
                mState.update(pos, false);
            }
        }

        binding.equalizerPreamp.setProgress((int) mEqualizer.getPreAmp() + 20);
        for (int i = 0; i < BAND_COUNT; ++i) {
            EqualizerBar bar = (EqualizerBar) binding.equalizerBands.getChildAt(i);
            if (bar != null)
                bar.setValue(mEqualizer.getAmp(i));
        }
        if (binding.equalizerButton.isChecked())
            mService.setEqualizer(mEqualizer);
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

    public class EqualizerState {

        boolean saved = true;
        public ObservableInt saveButtonVisibility = new ObservableInt(View.INVISIBLE);
        public ObservableInt revertButtonVisibility = new ObservableInt(View.INVISIBLE);
        public ObservableInt deleteButtonVisibility = new ObservableInt(View.INVISIBLE);

        public void update(int newPos, boolean newSaved) {
            saved = newSaved;
            saveButtonVisibility.set(newSaved ? View.INVISIBLE : View.VISIBLE);
            revertButtonVisibility.set(newSaved ? View.INVISIBLE : View.VISIBLE);
            deleteButtonVisibility.set(newSaved && getEqualizerType(newPos) == TYPE_CUSTOM ? View.VISIBLE : View.INVISIBLE);
        }
    }
}