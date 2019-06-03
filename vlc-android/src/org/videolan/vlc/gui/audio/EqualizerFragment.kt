/*
 *****************************************************************************
 * EqualizerFragment.java
 *****************************************************************************
 * Copyright © 2013-2019 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.audio

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableInt
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.EqualizerBinding
import org.videolan.vlc.gui.dialogs.VLCBottomSheetDialogFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.view.EqualizerBar
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.VLCOptions
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class EqualizerFragment : VLCBottomSheetDialogFragment() {
    override fun getDefaultState() = STATE_EXPANDED

    override fun needToManageOrientation() = false

    override fun initialFocusedView() = binding.equalizerBands.getChildAt(0)

    private lateinit var equalizer: MediaPlayer.Equalizer
    private var customCount = 0
    private var presetCount = 0
    private var allSets: MutableList<String> = ArrayList()
    private lateinit var adapter: ArrayAdapter<String>
    private var revertPos = 0
    private var savePos = 0
    private var updateAlreadyHandled = false
    private lateinit var binding: EqualizerBinding
    private val state = EqualizerState()
    private val newPresetName = VLCApplication.appResources.getString(R.string.equalizer_new_preset_name)

    private val mSetListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
            if (!binding.equalizerButton.isChecked && !updateAlreadyHandled)
                binding.equalizerButton.isChecked = true

            //save set if changes made (needs old currentPosition)
            if (savePos >= 0 && !state.saved && !updateAlreadyHandled)
                createSaveCustomSetDialog(savePos, displayedByUser = false, onPause = false)
            updateEqualizer(pos)
        }

        override fun onNothingSelected(parent: AdapterView<*>) {}
    }

    private val mPreampListener = object : OnSeekBarChangeListener {
        override fun onStopTrackingTouch(seekBar: SeekBar) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            equalizer.preAmp = (progress - 20).toFloat()
            if (!binding.equalizerButton.isChecked) binding.equalizerButton.isChecked = true

            val pos = binding.equalizerPresets.selectedItemPosition
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos
                savePos = presetCount + customCount
                state.update(presetCount + customCount, false)
                updateAlreadyHandled = true
                binding.equalizerPresets.setSelection(presetCount + customCount)
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos
                savePos = pos
                state.update(pos, false)
            }
            if (binding.equalizerButton.isChecked) PlaybackService.equalizer.value = equalizer
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DataBindingUtil.inflate(inflater, R.layout.equalizer, container, false)
        binding.state = state
        return binding.root
    }

    private fun fillViews() {
        val presets = equalizerPresets

        if (context == null || presets == null) return

        allSets.clear()
        allSets = ArrayList()
        allSets.addAll(Arrays.asList(*presets))
        presetCount = allSets.size
        for ((key) in Settings.getInstance(requireActivity()).all) {
            if (key.startsWith("custom_equalizer_")) {
                allSets.add(key.replace("custom_equalizer_", "").replace("_", " "))
                customCount++
            }
        }
        allSets.add(newPresetName)

        equalizer = VLCOptions.getEqualizerSetFromSettings(requireActivity(), true)!!

        // on/off
        binding.equalizerButton.isChecked = VLCOptions.getEqualizerEnabledState(requireActivity())
        binding.equalizerButton.setOnCheckedChangeListener { _, isChecked -> PlaybackService.equalizer.setValue(if (isChecked) equalizer else null) }
        binding.equalizerSave.setOnClickListener { createSaveCustomSetDialog(binding.equalizerPresets.selectedItemPosition, displayedByUser = true, onPause = false) }
        binding.equalizerDelete.setOnClickListener { createDeleteCustomSetSnacker() }
        binding.equalizerRevert.setOnClickListener { revertCustomSetChanges() }

        // presets
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allSets)
        binding.equalizerPresets.adapter = adapter

        // Set the default selection asynchronously to prevent a layout initialization bug.
        binding.equalizerPresets.post {
            binding.equalizerPresets.onItemSelectedListener = mSetListener
            val pos = allSets.indexOf(VLCOptions.getEqualizerNameFromSettings(requireActivity()))
            state.update(pos, VLCOptions.getEqualizerSavedState(requireActivity()))
            updateAlreadyHandled = true
            if (binding.equalizerButton.isChecked || !state.saved) {
                savePos = pos
                revertPos = if (getEqualizerType(pos) == TYPE_CUSTOM) pos else 0
                binding.equalizerPresets.setSelection(pos)
            } else {
                updateEqualizer(0)
            }
        }

        // preamp
        binding.equalizerPreamp.max = 40
        binding.equalizerPreamp.progress = equalizer.preAmp.toInt() + 20
        binding.equalizerPreamp.setOnSeekBarChangeListener(mPreampListener)

        // bands
        for (i in 0 until BAND_COUNT) {
            val band = MediaPlayer.Equalizer.getBandFrequency(i)

            val bar = EqualizerBar(requireContext(), band)
            bar.setValue(equalizer.getAmp(i))
            bar.setListener(BandListener(i))

            binding.equalizerBands.addView(bar)
            val params = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT, 1f)
            bar.layoutParams = params
        }
    }

    override fun onResume() {
        fillViews()
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.equalizerButton.setOnCheckedChangeListener(null)
        binding.equalizerPresets.onItemSelectedListener = null
        binding.equalizerPreamp.setOnSeekBarChangeListener(null)
        binding.equalizerBands.removeAllViews()
        if (binding.equalizerButton.isChecked) {
            val pos = binding.equalizerPresets.selectedItemPosition
            VLCOptions.saveEqualizerInSettings(requireActivity(), equalizer, allSets[pos], true, state.saved)
        } else {
            VLCOptions.saveEqualizerInSettings(requireActivity(), MediaPlayer.Equalizer.createFromPreset(0), allSets[0], false, true)
        }
        if (!state.saved)
            createSaveCustomSetDialog(binding.equalizerPresets.selectedItemPosition, displayedByUser = false, onPause = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackService.equalizer.clear()
    }

    private inner class BandListener internal constructor(private val index: Int) : OnEqualizerBarChangeListener {

        override fun onProgressChanged(value: Float, fromUser: Boolean) {
            if (!fromUser)
                return
            equalizer.setAmp(index, value)
            if (!binding.equalizerButton.isChecked)
                binding.equalizerButton.isChecked = true

            val pos = binding.equalizerPresets.selectedItemPosition
            if (getEqualizerType(pos) == TYPE_PRESET) {
                revertPos = pos
                savePos = presetCount + customCount
                state.update(presetCount + customCount, false)
                updateAlreadyHandled = true
                binding.equalizerPresets.setSelection(presetCount + customCount)
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                revertPos = pos
                savePos = pos
                state.update(pos, false)
            }

            if (binding.equalizerButton.isChecked) PlaybackService.equalizer.value = equalizer
        }
    }

    private fun createSaveCustomSetDialog(positionToSave: Int, displayedByUser: Boolean, onPause: Boolean) {
        val oldName = allSets[positionToSave]

        val temporarySet = MediaPlayer.Equalizer.create()
        temporarySet.preAmp = equalizer.preAmp
        for (i in 0 until MediaPlayer.Equalizer.getBandCount())
            temporarySet.setAmp(i, equalizer.getAmp(i))

        val input = EditText(context)
        input.setText(oldName)
        input.setSelectAllOnFocus(true)

        val saveEqualizer = AlertDialog.Builder(requireActivity())
                .setTitle(resources.getString(if (displayedByUser)
                    R.string.custom_set_save_title
                else
                    R.string.custom_set_save_warning))
                .setMessage(resources.getString(if (getEqualizerType(positionToSave) == TYPE_CUSTOM)
                    R.string.existing_custom_set_save_message
                else
                    R.string.new_custom_set_save_message))
                .setView(input)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.do_not_save) { _, _ ->
                    if (onPause)
                        VLCOptions.saveEqualizerInSettings(VLCApplication.appContext, equalizer, allSets[positionToSave], binding.equalizerButton.isChecked, false)
                }
                .setOnCancelListener {
                    if (onPause)
                        VLCOptions.saveEqualizerInSettings(VLCApplication.appContext, equalizer, allSets[positionToSave], binding.equalizerButton.isChecked, false)
                }
                .create()
        val window = saveEqualizer.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        //HACK to prevent closure
        saveEqualizer.setOnShowListener { dialog ->
            val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val newName = input.text.toString()
                if (newName.contains("_") || TextUtils.equals(newName, newPresetName)) {
                    Toast.makeText(context, VLCApplication.appContext.resources.getString(R.string.custom_set_wrong_input), Toast.LENGTH_SHORT).show()
                } else if (allSets.contains(newName) && !TextUtils.equals(newName, oldName)) {
                    Toast.makeText(context, VLCApplication.appContext.resources.getString(R.string.custom_set_already_exist), Toast.LENGTH_SHORT).show()
                } else {
                    VLCOptions.saveCustomSet(requireActivity(), temporarySet, newName)
                    if (onPause) {
                        if (binding.equalizerButton.isChecked)
                            VLCOptions.saveEqualizerInSettings(requireActivity(), temporarySet, newName, true, true)
                    } else {
                        if (TextUtils.equals(newName, oldName)) {
                            if (displayedByUser) {
                                state.update(allSets.indexOf(newName), true)
                            }
                        } else {
                            //insert new item before the one being saved in order to keep position
                            allSets.add(positionToSave, newName)
                            customCount++
                            if (displayedByUser) {
                                adapter.notifyDataSetChanged()
                                state.update(allSets.indexOf(newName), true)
                                updateAlreadyHandled = true
                            }
                        }
                    }
                    saveEqualizer.dismiss()
                }
            }
        }
        saveEqualizer.show()
    }

    private fun createDeleteCustomSetSnacker() {
        val oldPos = binding.equalizerPresets.selectedItemPosition
        val oldName = allSets[oldPos]
        if (getEqualizerType(oldPos) == TYPE_CUSTOM) {

            val savedEqualizerSet = VLCOptions.getCustomSet(requireActivity(), oldName) ?: return
            val cancelAction = Runnable {
                VLCOptions.saveCustomSet(requireActivity(), savedEqualizerSet, oldName)
                equalizer = savedEqualizerSet
                allSets.add(oldPos, oldName)
                customCount++
                binding.equalizerPresets.setSelection(oldPos)
            }

            VLCOptions.deleteCustomSet(requireActivity(), oldName)
            allSets.remove(oldName)
            customCount--
            state.update(0, true)
            binding.equalizerPresets.setSelection(0)
            val message = getString(R.string.custom_set_deleted_message, oldName)
            UiTools.snackerWithCancel(binding.root, message, null, cancelAction)
        }
    }

    private fun revertCustomSetChanges() {
        val pos = binding.equalizerPresets.selectedItemPosition

        val temporarySet = MediaPlayer.Equalizer.create()
        temporarySet.preAmp = equalizer.preAmp
        for (i in 0 until MediaPlayer.Equalizer.getBandCount())
            temporarySet.setAmp(i, equalizer.getAmp(i))

        val cancelAction = Runnable {
            state.update(pos, false)
            equalizer = temporarySet
            updateAlreadyHandled = true
            if (pos == revertPos)
                updateEqualizer(pos)
            else
                binding.equalizerPresets.setSelection(pos)
        }
        state.update(revertPos, true)
        if (pos == revertPos)
            updateEqualizer(revertPos)
        else
            binding.equalizerPresets.setSelection(revertPos)

        val message = if (getEqualizerType(pos) == TYPE_CUSTOM)
            getString(R.string.custom_set_restored)
        else
            getString(R.string.unsaved_set_deleted_message)
        UiTools.snackerWithCancel(binding.root, message, null, cancelAction)
    }

    private fun updateEqualizer(pos: Int) {
        if (updateAlreadyHandled) {
            updateAlreadyHandled = false
        } else {
            when {
                getEqualizerType(pos) == TYPE_PRESET -> {
                    equalizer = MediaPlayer.Equalizer.createFromPreset(pos)
                    state.update(pos, true)
                }
                getEqualizerType(pos) == TYPE_CUSTOM -> {
                    equalizer = VLCOptions.getCustomSet(requireActivity(), allSets[pos])!!
                    state.update(pos, true)
                }
                getEqualizerType(pos) == TYPE_NEW -> {
                    equalizer = MediaPlayer.Equalizer.create()
                    state.update(pos, false)
                }
            }
        }

        binding.equalizerPreamp.progress = equalizer.preAmp.toInt() + 20
        for (i in 0 until BAND_COUNT) {
            val bar = binding.equalizerBands.getChildAt(i) as EqualizerBar
            bar.setValue(equalizer.getAmp(i))
        }
        if (binding.equalizerButton.isChecked) PlaybackService.equalizer.value = equalizer
    }

    private fun getEqualizerType(position: Int): Int {
        if (position < 0) return -1
        if (position < presetCount) return TYPE_PRESET
        return if (position < presetCount + customCount) TYPE_CUSTOM else TYPE_NEW
    }

    inner class EqualizerState {

        internal var saved = true
        var saveButtonVisibility = ObservableInt(View.INVISIBLE)
        var revertButtonVisibility = ObservableInt(View.INVISIBLE)
        var deleteButtonVisibility = ObservableInt(View.INVISIBLE)

        fun update(newPos: Int, newSaved: Boolean) {
            saved = newSaved
            saveButtonVisibility.set(if (newSaved) View.INVISIBLE else View.VISIBLE)
            revertButtonVisibility.set(if (newSaved) View.INVISIBLE else View.VISIBLE)
            deleteButtonVisibility.set(if (newSaved && getEqualizerType(newPos) == TYPE_CUSTOM) View.VISIBLE else View.INVISIBLE)
        }
    }

    companion object {

        const val TAG = "VLC/EqualizerFragment"
        private val BAND_COUNT = MediaPlayer.Equalizer.getBandCount()

        private const val TYPE_PRESET = 0
        private const val TYPE_CUSTOM = 1
        private const val TYPE_NEW = 2

        private val equalizerPresets: Array<String>?
            get() {
                val count = MediaPlayer.Equalizer.getPresetCount()
                if (count <= 0) return null
                val presets = ArrayList<String>(count)
                for (i in 0 until count) {
                    presets.add(MediaPlayer.Equalizer.getPresetName(i))
                }
                return presets.toTypedArray()
            }
    }
}