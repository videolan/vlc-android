/*
 * ************************************************************************
 *  EqualizerFragmentDialog.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */
package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogEqualizerBinding
import org.videolan.vlc.gui.view.EqualizerBar
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


/**
 * Dialog showing the audio equalizer
 */
class EqualizerFragmentDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogEqualizerBinding
    private val state = EqualizerState()

    private var bandCount = -1
    private var customCount = 0
    private var allSets: MutableList<String> = ArrayList()
    private val eqBandsViews = ArrayList<EqualizerBar>()

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

    private lateinit var equalizer: MediaPlayer.Equalizer


    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation() = true

    override fun initialFocusedView(): View = binding.equalizerContainer

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogEqualizerBinding.inflate(layoutInflater, container, false)
        binding.state = state
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    override fun onResume() {
        equalizer = VLCOptions.getEqualizerSetFromSettings(requireActivity(), true)!!
        lifecycleScope.launch {
            if (bandCount == -1) bandCount = withContext(Dispatchers.IO) {
                VLCInstance.getInstance(requireContext())
                MediaPlayer.Equalizer.getBandCount()
            }
            fillViews()
        }
        super.onResume()
    }

    /**
     * Populate the list of presets
     *
     */
    fun populateAllSet() {
        val presets = equalizerPresets
        allSets.clear()
        allSets = ArrayList()
        for ((key) in Settings.getInstance(requireActivity()).all) {
            if (key.startsWith("custom_equalizer_")) {
                allSets.add(key.replace("custom_equalizer_", "").replace("_", " "))
                customCount++
            }
        }
        customCount = allSets.size
        presets?.let { allSets.addAll(it) }
    }

    /**
     * Fill the views with the current equalizer
     *
     */
    private fun fillViews() = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {

        populateAllSet()

        val pos = allSets.indexOf(VLCOptions.getEqualizerNameFromSettings(requireActivity()))

        // on/off
        binding.equalizerButton.isChecked = VLCOptions.getEqualizerEnabledState(requireActivity())
        binding.equalizerButton.setOnCheckedChangeListener { _, isChecked ->
            PlaybackService.equalizer.setValue(if (isChecked) equalizer else null)
            updateEnabledState()
        }

        var selectedChip: Chip? = null
        binding.equalizerPresets.removeAllViews()
        allSets.forEachIndexed { index, item ->
            val chip = Chip(requireActivity())
            chip.text = item
            chip.tag = item
            chip.isCheckable = true
            if (index < customCount) chip.setChipBackgroundColorResource(R.color.orange_800_transparent_10)
            if (index == pos) selectedChip = chip
            chip.setOnClickListener {
                selectPreset(it.tag.toString())
            }
            binding.equalizerPresets.addView(chip)
        }

        binding.equalizerPresetsContainer.post {
            selectPreset(allSets[pos])
            binding.equalizerPresetsContainer.scrollTo(selectedChip!!.left, selectedChip!!.top)
        }

        fillBands()
        updateEnabledState()

    }

    private fun getCurrentPosition(): Int = allSets.indexOf(binding.state?.name?.get())

    /**
     * Fill the equalizer bands
     *
     */
    fun fillBands() {
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
            bar.setSliderId(View.generateViewId())
            bar.setListener(BandListener(i))
        }
        for (i in 0 until bandCount) {
            if (i > 0) eqBandsViews[i].nextFocusLeftId = eqBandsViews[i - 1].getSliderId()
            if (i < bandCount - 1) eqBandsViews[i].nextFocusRightId = eqBandsViews[i + 1].getSliderId()
        }
        eqBandsViews[0].nextFocusLeftId = R.id.equalizer_preamp
        eqBandsViews[eqBandsViews.size - 1].nextFocusRightId = R.id.snapBands
    }

    fun updateEnabledState() {
        val isChecked = binding.equalizerButton.isChecked
        binding.equalizerPresets.children.forEach {
            it.isEnabled = isChecked
        }
        val eqCardEnabled = isChecked && getEqualizerType(getCurrentPosition()) == TYPE_CUSTOM
        binding.equalizerPreamp.isEnabled = eqCardEnabled
        binding.equalizerBands.children.forEach {
            it.isEnabled = eqCardEnabled
        }
        binding.snapBands.isEnabled = eqCardEnabled
    }

    /**
     * Select preset by name and update the equalizer
     *
     * @param name the name of the preset
     */
    fun selectPreset(name: String) {
        val pos = allSets.indexOf(name)
        updateEqualizer(pos)
        val preset = allSets[pos]
        state.name.set(preset)
        state.type.set(getEqualizerType(pos))
        val selectedChip = binding.equalizerPresets.findViewWithTag<Chip>(preset)
        binding.equalizerPresets.check(selectedChip.id)
        updateEnabledState()
    }

    /**
     * Update the equalizer with the selected preset
     *
     * @param pos the position of the preset
     */
    fun updateEqualizer(pos: Int) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        when {
            getEqualizerType(pos) == TYPE_PRESET -> {
                equalizer = MediaPlayer.Equalizer.createFromPreset(pos - customCount)
                state.update(pos, true)
            }

            getEqualizerType(pos) == TYPE_CUSTOM -> {
                equalizer = VLCOptions.getCustomSet(requireActivity(), allSets[pos])!!
                state.update(pos, true)
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

    /**
     * Get the equalizer type
     *
     * @param position the position of the equalizer
     * @return the type of the equalizer
     */
    private fun getEqualizerType(position: Int): Int {
        if (position < 0) return -1
        if (position < customCount) return TYPE_CUSTOM
        return TYPE_PRESET
    }

    /**
     * Get equalizer name
     *
     * @param position the position of the equalizer
     * @return the name of the equalizer
     */
    private fun getEqualizerName(position: Int): String {
        if (position < 0) return "Flat"
        return allSets[position]
    }


    companion object {

        private const val TYPE_PRESET = 0
        private const val TYPE_CUSTOM = 1

        fun newInstance(): EqualizerFragmentDialog {
            return EqualizerFragmentDialog()
        }
    }

    /**
     * Equalizer state
     *
     * @constructor Create empty Equalizer state
     */
    inner class EqualizerState {

        internal var saved = true
        var saveButtonVisibility = ObservableBoolean(false)
        var revertButtonVisibility = ObservableBoolean(false)
        var deleteButtonVisibility = ObservableBoolean(false)
        var name = ObservableField<String>()
        var type = ObservableInt(TYPE_PRESET)

        fun update(newPos: Int, newSaved: Boolean) {
            saved = newSaved
            name.set(getEqualizerName(newPos))
            type.set(getEqualizerType(newPos))
            saveButtonVisibility.set(!newSaved)
            revertButtonVisibility.set(!newSaved)
            deleteButtonVisibility.set(newSaved && getEqualizerType(newPos) == TYPE_CUSTOM)
        }
    }

    /**
     * Band listener
     *
     * @property index the band index
     * @constructor Create empty Band listener
     */
    private inner class BandListener(private val index: Int) : OnEqualizerBarChangeListener {

        private var oldBands: MutableList<Int> = ArrayList()


        override fun onProgressChanged(value: Float, fromUser: Boolean) {
            if (!fromUser)
                return
            equalizer.setAmp(index, value)
            if (!binding.equalizerButton.isChecked)
                binding.equalizerButton.isChecked = true

            val pos = getCurrentPosition()
            if (getEqualizerType(pos) == TYPE_PRESET) {
                state.update(getCurrentPosition(), false)
            } else if (getEqualizerType(pos) == TYPE_CUSTOM) {
                state.update(pos, false)
            }

            /**
             * Snap bands
             */
            if (binding.snapBands.isChecked && oldBands.isNotEmpty()) {
                val delta = eqBandsViews[index].getProgress() - oldBands[index]
                for (i in eqBandsViews.indices) {
                    if (i == index) {
                        continue
                    }
                    eqBandsViews[i].setProgress((oldBands[i] + delta / ((i - index).absoluteValue.let { it * it * it } + 1)).coerceIn(0, EqualizerBar.RANGE * 2))

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

        override fun onStopTrackingTouch() {
            oldBands.clear()
        }
    }

}





