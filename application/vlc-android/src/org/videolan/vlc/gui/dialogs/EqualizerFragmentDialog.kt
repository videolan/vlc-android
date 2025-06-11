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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.core.view.children
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogEqualizerBinding
import org.videolan.vlc.gui.view.EqualizerBar
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.viewmodels.EqualizerViewModel
import org.videolan.vlc.viewmodels.EqualizerViewModelFactory
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


/**
 * Dialog showing the audio equalizer
 */
class EqualizerFragmentDialog : VLCBottomSheetDialogFragment(), Slider.OnChangeListener {

    private val viewModel: EqualizerViewModel by viewModels {
        EqualizerViewModelFactory(requireActivity(), EqualizerRepository.getInstance(requireActivity().application))
    }

    private lateinit var binding: DialogEqualizerBinding
    private val state = EqualizerState()

    private var customCount = 0
    private val eqBandsViews = ArrayList<EqualizerBar>()

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
        viewModel.equalizerEntries.observe(this) {
            fillViews()
        }
    }

    override fun onResume() {
        lifecycleScope.launch {
            if (viewModel.bandCount == -1) viewModel.bandCount = withContext(Dispatchers.IO) {
                VLCInstance.getInstance(requireContext())
                MediaPlayer.Equalizer.getBandCount()
            }
        }
        super.onResume()
    }

    /**
     * Fill the views with the current equalizer
     *
     */
    private fun fillViews() = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {

        // on/off
        binding.equalizerButton.isChecked = VLCOptions.getEqualizerEnabledState(requireActivity())
        binding.equalizerButton.setOnCheckedChangeListener { _, isChecked ->
            updateEnabledState()
            viewModel.updateEqualizer()
        }

        // preamp
        binding.equalizerPreamp.value = viewModel.getCurrentEqualizer().equalizerEntry.preamp.roundToInt().toFloat()
        binding.equalizerPreamp.addOnChangeListener(this@EqualizerFragmentDialog)

        binding.undo.setOnClickListener {
            viewModel.undoFromHistory(requireActivity())
            updateBars()
        }

        //edit
        binding.edit.setOnClickListener {
                viewModel.createCustomEqualizer(requireActivity())
        }


        var selectedChip: Chip? = null
        binding.equalizerPresets.removeAllViews()
        viewModel.equalizerEntries.value?.forEachIndexed { index, item ->
            val chip = Chip(requireActivity())
            chip.text = item.equalizerEntry.name
            chip.tag = item.equalizerEntry.id
            chip.isCheckable = true
            if (index < customCount) chip.setChipBackgroundColorResource(R.color.orange_800_transparent_10)
            if (item.equalizerEntry.id == viewModel.currentEqualizerId) selectedChip = chip
            chip.setOnClickListener {
                viewModel.currentEqualizerId = it.tag as Long
                selectPreset()
            }
            binding.equalizerPresets.addView(chip)
        }

        binding.equalizerPresetsContainer.post {
            selectPreset()
            binding.equalizerPresetsContainer.scrollTo(selectedChip!!.left, selectedChip!!.top)
        }

        fillBands()
        updateEnabledState()

    }

    /**
     * Fill the equalizer bands
     *
     */
    fun fillBands() {
        eqBandsViews.clear()
        binding.equalizerBands.removeAllViews()
        // bands
        val currentEqualizer = viewModel.getCurrentEqualizer()
        currentEqualizer.bands.forEach { band ->
            val bandFrequency = MediaPlayer.Equalizer.getBandFrequency(band.index)
            val bar = EqualizerBar(requireContext(), bandFrequency)
            bar.setValue(band.bandValue)

            binding.equalizerBands.addView(bar)

            val params = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f)
            bar.layoutParams = params
            if (BuildConfig.DEBUG) Log.d(TAG, "Add new band for ${band.index}")
            eqBandsViews.add(bar)
            bar.setSliderId(View.generateViewId())
            bar.setListener(BandListener(band.index))
        }
        currentEqualizer.bands.forEachIndexed {index, band ->
            if (index > 0) eqBandsViews[index].nextFocusLeftId = eqBandsViews[index - 1].getSliderId()
            if (index < currentEqualizer.bands.size - 1) eqBandsViews[index].nextFocusRightId = eqBandsViews[index + 1].getSliderId()
        }
        if (eqBandsViews.isNotEmpty()) {
            eqBandsViews[0].nextFocusLeftId = R.id.equalizer_preamp
            eqBandsViews[eqBandsViews.size - 1].nextFocusRightId = R.id.snapBands
        }
    }

    fun updateEnabledState() {
        val isChecked = binding.equalizerButton.isChecked
        Settings.getInstance(requireActivity()).edit { putBoolean("equalizer_enabled", isChecked) }
        binding.equalizerPresets.children.forEach {
            it.isEnabled = isChecked
        }
        val eqCardEnabled = isChecked && getEqualizerType() == TYPE_CUSTOM
        binding.equalizerPreamp.isEnabled = eqCardEnabled
        binding.equalizerBands.children.forEach {
            it.isEnabled = eqCardEnabled
        }
        binding.snapBands.isEnabled = eqCardEnabled
    }

    /**
     * Select the current preset and update the equalizer
     *
     */
    fun selectPreset() {
        updateEqualizer()
        state.name.set(viewModel.getCurrentEqualizer().equalizerEntry.name)
        val selectedChip = binding.equalizerPresets.findViewWithTag<Chip>(viewModel.getCurrentEqualizer().equalizerEntry.id)
        binding.equalizerPresets.check(selectedChip.id)
        updateEnabledState()
    }

    /**
     * Update the equalizer with the selected preset
     *
     */
    fun updateEqualizer() = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        if (viewModel.bandCount == -1) viewModel.bandCount = withContext(Dispatchers.IO) {
            VLCInstance.getInstance(requireContext())
            MediaPlayer.Equalizer.getBandCount()
        }
        updateBars()
        if (binding.equalizerButton.isChecked) viewModel.updateEqualizer()
    }

    /**
     * Update bars with the current equalizer
     *
     */
    private fun updateBars() {
        if (!isStarted()) return

        binding.equalizerPreamp.value = viewModel.getCurrentEqualizer().equalizerEntry.preamp.roundToInt().toFloat()
        for (i in 0 until viewModel.bandCount) {
            val bar = binding.equalizerBands.getChildAt(i) as EqualizerBar
            bar.setValue(viewModel.getCurrentEqualizer().bands.first { it.index == i }.bandValue)
        }
    }

    /**
     * FIXME: move to EqualizerEntity class
     * Get the equalizer type
     *
     * @return the type of the equalizer
     */
    private fun getEqualizerType(): Int {
        if (viewModel.getCurrentEqualizer().equalizerEntry.presetIndex == -1) return TYPE_CUSTOM
        return TYPE_PRESET
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) return
        viewModel.saveInHistory(-1)
        viewModel.updateCurrentPreamp(requireActivity(), binding.equalizerPreamp.value)
        if (binding.equalizerButton.isChecked) viewModel.updateEqualizer()
    }


    companion object {
        const val TAG = "VLC/EqualizerFragmentDialog"

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

        fun update(newSaved: Boolean) {
            saved = newSaved
            name.set(viewModel.getCurrentEqualizer().equalizerEntry.name)
            type.set(getEqualizerType())
            saveButtonVisibility.set(!newSaved)
            revertButtonVisibility.set(!newSaved)
            deleteButtonVisibility.set(newSaved && getEqualizerType() == TYPE_CUSTOM)
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
            viewModel.saveInHistory(index)

            val newBandList = ArrayList<EqualizerBand>()
            newBandList.add(viewModel.getCurrentEqualizer().bands.first { it.index ==  index}.copy(bandValue = value))
            if (!binding.equalizerButton.isChecked)
                binding.equalizerButton.isChecked = true

            if (getEqualizerType() == TYPE_PRESET) {
                state.update(false)
            } else if (getEqualizerType() == TYPE_CUSTOM) {
                state.update(false)
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

                    newBandList.add(viewModel.getCurrentEqualizer().bands[i].copy(bandValue = eqBandsViews[i].getValue()))
                }
            }
            
            viewModel.getCurrentEqualizer().bands.forEach { oldBand ->
                if (newBandList.firstOrNull { it.index == oldBand.index } == null)
                    newBandList.add(oldBand)
            }
            viewModel.updateEqualizerBands(requireActivity(), newBandList)

            if (binding.equalizerButton.isChecked) viewModel.updateEqualizer()
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





