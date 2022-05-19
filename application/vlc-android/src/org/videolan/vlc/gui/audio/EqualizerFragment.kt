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

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.EqualizerBinding
import org.videolan.vlc.gui.dialogs.VLCBottomSheetDialogFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.view.EqualizerBar
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener
import kotlin.math.abs
import kotlin.math.roundToInt

class EqualizerFragment : VLCBottomSheetDialogFragment(), Slider.OnChangeListener {
    override fun getDefaultState() = STATE_EXPANDED

    override fun needToManageOrientation() = true

    override fun initialFocusedView(): View = binding.equalizerContainer

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
    private val newPresetName = AppContextProvider.appResources.getString(R.string.equalizer_new_preset_name)
    private var bandCount = -1

    private val eqBandsViews = ArrayList<EqualizerBar>()

    private val setListener = object : OnItemSelectedListener {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DataBindingUtil.inflate(inflater, R.layout.equalizer, container, false)
        binding.state = state
        customCount = 0
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.equalizerBands.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            true
        }
    }

    private fun fillViews() = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        val presets = equalizerPresets

        if (context == null || presets == null) return@launch

        if (bandCount == -1) bandCount = withContext(Dispatchers.IO) {
            VLCInstance.getInstance(requireContext())
            MediaPlayer.Equalizer.getBandCount()
        }
        if (!isStarted()) return@launch

        allSets.clear()
        allSets = ArrayList()
        allSets.addAll(listOf(*presets))
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

        // preamp
        binding.equalizerPreamp.value = equalizer.preAmp.roundToInt().toFloat()
        binding.equalizerPreamp.addOnChangeListener(this@EqualizerFragment)

        eqBandsViews.clear()
        binding.equalizerBands.removeAllViews()
        // bands
        for (i in 0 until bandCount) {
            val band = MediaPlayer.Equalizer.getBandFrequency(i)

            val bar = EqualizerBar(requireContext(), band)
            bar.setValue(equalizer.getAmp(i))

            binding.equalizerBands.addView(bar)

            val params = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f)
            bar.layoutParams = params
            eqBandsViews.add(bar)
            bar.setListener(BandListener(i))
        }

        eqBandsViews[0].nextFocusLeftId = R.id.equalizer_preamp
        eqBandsViews[eqBandsViews.size - 1].nextFocusRightId = R.id.snapBands

        // Set the default selection asynchronously to prevent a layout initialization bug.
        binding.equalizerPresets.post {
            val activity = activity ?: return@post
            binding.equalizerPresets.onItemSelectedListener = setListener
            val pos = allSets.indexOf(VLCOptions.getEqualizerNameFromSettings(activity))
            state.update(pos, VLCOptions.getEqualizerSavedState(activity))
            updateAlreadyHandled = true
            if (binding.equalizerButton.isChecked || !state.saved) {
                savePos = pos
                revertPos = if (getEqualizerType(pos) == TYPE_CUSTOM) pos else 0
                binding.equalizerPresets.setSelection(pos)
            } else {
                updateEqualizer(0)
            }
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
        binding.equalizerPreamp.removeOnChangeListener(this)
        if (binding.equalizerButton.isChecked) {
            val pos = binding.equalizerPresets.selectedItemPosition
            if (::equalizer.isInitialized) VLCOptions.saveEqualizerInSettings(requireActivity(), equalizer, allSets[pos], true, state.saved)
        } else {
            VLCOptions.saveEqualizerInSettings(requireActivity(), MediaPlayer.Equalizer.createFromPreset(0), allSets[0], enabled = false, saved = true)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        eqBandsViews.forEach { it.setListener(null) }
        super.onDismiss(dialog)
        if (!state.saved)
            createSaveCustomSetDialog(binding.equalizerPresets.selectedItemPosition, displayedByUser = false, onPause = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackService.equalizer.clear()
    }

    private inner class BandListener(private val index: Int) : OnEqualizerBarChangeListener {

        private var oldBands: MutableList<Int> = ArrayList()


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


            if (binding.snapBands.isChecked) {
                val delta = eqBandsViews[index].getProgress() - oldBands[index]
                for (i in eqBandsViews.indices) {
                    if (i == index) {
                        continue
                    }

                    eqBandsViews[i].setProgress(oldBands[i] + delta / (abs(i - index) * abs(i - index) * abs(i - index) + 1))

                    if (binding.equalizerButton.isChecked) {

                        equalizer.setAmp(i, eqBandsViews[i].getValue())
                    }

                }
            }

            if (binding.equalizerButton.isChecked) PlaybackService.equalizer.value = equalizer


        }

        override fun onStartTrackingTouch() {
            oldBands.clear()
            for (eqBandsView in eqBandsViews) {
                oldBands.add(eqBandsView.getProgress())
            }
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
        input.setSingleLine()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (context == null) return
                val newName = input.text.toString()
                if (input.text.contains("_") || newName == newPresetName) {
                    input.error = getString(R.string.custom_set_wrong_input)
                    Toast.makeText(requireActivity(), AppContextProvider.appContext.resources.getString(R.string.custom_set_wrong_input), Toast.LENGTH_SHORT).show()
                } else if (allSets.contains(newName) && newName != oldName) {
                    input.error = getString(R.string.custom_set_already_exist)
                } else input.error = null
            }

            override fun afterTextChanged(s: Editable?) { }
        })


        val container = FrameLayout(requireContext())
        val klNormal = resources.getDimension(R.dimen.kl_normal).toInt()
        container.setPadding(klNormal, 0, klNormal, 0)

        container.addView(input)

        val saveEqualizer = AlertDialog.Builder(requireActivity())
                .setTitle(resources.getString(if (displayedByUser)
                    R.string.custom_set_save_title
                else
                    R.string.custom_set_save_warning))
                .setMessage(resources.getString(if (getEqualizerType(positionToSave) == TYPE_CUSTOM)
                    R.string.existing_custom_set_save_message
                else
                    R.string.new_custom_set_save_message))
                .setView(container)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.do_not_save) { _, _ ->
                    if (onPause)
                        VLCOptions.saveEqualizerInSettings(AppContextProvider.appContext, equalizer, allSets[positionToSave], binding.equalizerButton.isChecked, false)
                }
                .setOnCancelListener {
                    if (onPause)
                        VLCOptions.saveEqualizerInSettings(AppContextProvider.appContext, equalizer, allSets[positionToSave], binding.equalizerButton.isChecked, false)
                }
                .create()
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                //Perform Code
                if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Enter pressed")
                save(saveEqualizer.context, input, oldName, temporarySet, onPause, displayedByUser, positionToSave, saveEqualizer)
                true
            } else false
        }
        val window = saveEqualizer.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        //HACK to prevent closure
        saveEqualizer.setOnShowListener { dialog ->
            val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                save(saveEqualizer.context, input, oldName, temporarySet, onPause, displayedByUser, positionToSave, saveEqualizer)
            }
        }
        saveEqualizer.show()
    }

    private fun save(ctx: Context, input: EditText, oldName: String, temporarySet: MediaPlayer.Equalizer, onPause: Boolean, displayedByUser: Boolean, positionToSave: Int, saveEqualizer: AlertDialog) {
        if (input.error == null) {
            val newName = input.text.toString()
            VLCOptions.saveCustomSet(ctx, temporarySet, newName)
            if (onPause) {
                if (binding.equalizerButton.isChecked)
                    VLCOptions.saveEqualizerInSettings(ctx, temporarySet, newName, enabled = true, saved = true)
            } else {
                if (newName == oldName) {
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

    private fun createDeleteCustomSetSnacker() {
        val oldPos = binding.equalizerPresets.selectedItemPosition
        val oldName = allSets[oldPos]
        if (getEqualizerType(oldPos) == TYPE_CUSTOM) {

            val savedEqualizerSet = VLCOptions.getCustomSet(requireActivity(), oldName) ?: return
            VLCOptions.deleteCustomSet(requireActivity(), oldName)
            allSets.remove(oldName)
            customCount--
            state.update(0, true)
            binding.equalizerPresets.setSelection(0)
            val message = getString(R.string.custom_set_deleted_message, oldName)
            UiTools.snackerWithCancel(requireActivity(), message, action = {}) {
                VLCOptions.saveCustomSet(requireActivity(), savedEqualizerSet, oldName)
                equalizer = savedEqualizerSet
                allSets.add(oldPos, oldName)
                customCount++
                binding.equalizerPresets.setSelection(oldPos)
            }
        }
    }

    private fun revertCustomSetChanges() {
        val pos = binding.equalizerPresets.selectedItemPosition

        val temporarySet = MediaPlayer.Equalizer.create()
        temporarySet.preAmp = equalizer.preAmp
        for (i in 0 until MediaPlayer.Equalizer.getBandCount())
            temporarySet.setAmp(i, equalizer.getAmp(i))

        state.update(revertPos, true)
        if (pos == revertPos)
            updateEqualizer(revertPos)
        else
            binding.equalizerPresets.setSelection(revertPos)

        val message = if (getEqualizerType(pos) == TYPE_CUSTOM)
            getString(R.string.custom_set_restored)
        else
            getString(R.string.unsaved_set_deleted_message)
        UiTools.snackerWithCancel(requireActivity(), message, action = {}) {
            state.update(pos, false)
            equalizer = temporarySet
            updateAlreadyHandled = true
            if (pos == revertPos)
                updateEqualizer(pos)
            else
                binding.equalizerPresets.setSelection(pos)
        }
    }

    private fun updateEqualizer(pos: Int) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        if (bandCount == -1) bandCount = withContext(Dispatchers.IO) {
            VLCInstance.getInstance(requireContext())
            MediaPlayer.Equalizer.getBandCount()
        }
        if (!isStarted()) return@launch

        binding.equalizerPreamp.value = equalizer.preAmp.roundToInt().toFloat()
        for (i in 0 until bandCount) {
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
        var saveButtonVisibility = ObservableBoolean(false)
        var revertButtonVisibility = ObservableBoolean(false)
        var deleteButtonVisibility = ObservableBoolean(false)

        fun update(newPos: Int, newSaved: Boolean) {
            saved = newSaved
            saveButtonVisibility.set(!newSaved)
            revertButtonVisibility.set(!newSaved)
            deleteButtonVisibility.set(newSaved && getEqualizerType(newPos) == TYPE_CUSTOM)
        }
    }

    companion object {

        const val TAG = "VLC/EqualizerFragment"

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

        fun newInstance(): EqualizerFragment {
            return EqualizerFragment()
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) return
        equalizer.preAmp = binding.equalizerPreamp.value
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