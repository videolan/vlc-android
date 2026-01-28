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

import android.animation.LayoutTransition
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.core.view.children
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
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.isStarted
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogEqualizerBinding
import org.videolan.vlc.gui.EqualizerSettingsActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.EqualizerBar
import org.videolan.vlc.interfaces.OnEqualizerBarChangeListener
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.viewmodels.EqualizerViewModel
import org.videolan.vlc.viewmodels.EqualizerViewModelFactory
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import org.videolan.vlc.viewmodels.EqualizerViewModel.Companion.currentEqualizerIdLive


/**
 * Dialog showing the audio equalizer
 */
class EqualizerFragmentDialog : VLCBottomSheetDialogFragment(), Slider.OnChangeListener {

    private val viewModel: EqualizerViewModel by viewModels {
        EqualizerViewModelFactory(requireActivity(), EqualizerRepository.getInstance(requireActivity().application))
    }

    private lateinit var binding: DialogEqualizerBinding

    private val eqBandsViews = ArrayList<EqualizerBar>()
    var oldEqualiserSets = listOf<String>()
    var oldCurrentEqualizer: EqualizerWithBands? = null

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (requireActivity() is EqualizerSettingsActivity) {
            binding.equalizerSettings.setGone()
        }
        viewModel.equalizerEntries.observe(this) {
            val newEqualizerSets = it.map { it.equalizerEntry.name }

            if (oldEqualiserSets != newEqualizerSets) fillPresets()
            if (oldCurrentEqualizer?.equalizerEntry?.preamp != viewModel.getCurrentEqualizer().equalizerEntry.preamp) {
                fillPreamp()
            }

            if (binding.equalizerBands.isEmpty() || viewModel.needForceRefresh || oldCurrentEqualizer == null || oldCurrentEqualizer?.equalizerEntry?.id != viewModel.getCurrentEqualizer().equalizerEntry.id) {
                fillBands()
                viewModel.needForceRefresh = false
                binding.name = viewModel.getCurrentEqualizer().equalizerEntry.name
                updateEnabledState()
            }
            binding.undo.isEnabled = viewModel.history.isNotEmpty()
            if (oldCurrentEqualizer == null) fillViews()
            oldEqualiserSets = newEqualizerSets
            oldCurrentEqualizer = viewModel.getCurrentEqualizer()
            updateEqualizer(true)
        }
    }

    override fun onResume() {
        lifecycleScope.launch {
            if (viewModel.bandCount == -1) viewModel.bandCount = withContext(Dispatchers.IO) {
                VLCInstance.getInstance(requireContext())
                MediaPlayer.Equalizer.getBandCount()
            }
        }
        if (binding.equalizerPresets.isNotEmpty() && oldCurrentEqualizer != null && oldCurrentEqualizer?.equalizerEntry?.id != currentEqualizerIdLive.value) {
            binding.equalizerPresets.children.forEach {
                if (it.tag == currentEqualizerIdLive.value) it.performClick()
            }
        }
        //Workaround fix for bottom sheet bug with animateLayoutChanges
        val transition = LayoutTransition()
        transition.setAnimateParentHierarchy(false)
        binding.equalizerContainer.layoutTransition = transition
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

        binding.equalizerSettings.setOnClickListener {
            if (requireActivity() is VideoPlayerActivity)
                UiTools.snackerConfirm(requireActivity(), getString(R.string.equalizer_leave_warning), forcedView = binding.contextMenuItemSnackbarHost) {
                    startActivity(Intent(requireActivity(), EqualizerSettingsActivity::class.java))
                }
            else
                startActivity(Intent(requireActivity(), EqualizerSettingsActivity::class.java))
        }

        binding.equalizerAdd.setOnClickListener {
            viewModel.createCustomEqualizer(requireActivity(), true)
            if (!binding.equalizerButton.isChecked) binding.equalizerButton.isChecked = true
        }

        binding.undo.setOnClickListener {
            viewModel.undoFromHistory(requireActivity())
            updateBars()
        }

        binding.delete.setOnClickListener {
            viewModel.presetToDelete = viewModel.getCurrentEqualizer()
            val message = if (getEqualizerType() == TYPE_CUSTOM) {
                 getString(R.string.confirm_delete_eq)
            } else {
                getString(R.string.confirm_delete_vlc_eq)
            }
            UiTools.snackerConfirm(requireActivity(), message, forcedView = binding.contextMenuItemSnackbarHost) {
                viewModel.deleteEqualizer(requireActivity())
            }
        }

        binding.edit.setOnClickListener {
            viewModel.createCustomEqualizer(requireActivity())
        }

        binding.presetTitleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() == viewModel.getCurrentEqualizer().equalizerEntry.name) return
                if (!viewModel.isNameAllowed(s.toString())) {
                    binding.presetTitleEdit.error = getString(R.string.edit_eq_name_not_allowed)
                } else {
                    viewModel.updateEqualizerName(requireActivity(), s.toString())
                    binding.presetTitleEdit.error = null
                }
            }
            override fun afterTextChanged(s: Editable?) { }
        })
        updateEnabledState()

    }

    private fun fillPreamp() {
        // preamp
        binding.equalizerPreamp.value = viewModel.getCurrentEqualizer().equalizerEntry.preamp.roundToInt().toFloat()
        binding.equalizerPreamp.addOnChangeListener(this@EqualizerFragmentDialog)
    }

    private fun fillPresets() {
        var selectedChip: Chip? = null
        var selectedChipIndex = 0

        // Refresh instead of recreating
        if (binding.equalizerPresets.children.count() == viewModel.equalizerEntries.value?.count()) {
            binding.equalizerPresets.children.forEachIndexed {index, chip ->
                (chip as Chip).let {
                    it.tag = viewModel.equalizerEntries.value?.get(index)?.equalizerEntry?.id
                    it.text = viewModel.equalizerEntries.value?.get(index)?.equalizerEntry?.name
                }
            }
            return
        }
        binding.equalizerPresets.removeAllViews()
        viewModel.equalizerEntries.value?.forEachIndexed { index, item ->
            val chip = Chip(requireActivity())
            chip.text = item.equalizerEntry.name
            chip.tag = item.equalizerEntry.id
            chip.isCheckable = true
            if (item.equalizerEntry.presetIndex == -1) chip.setChipBackgroundColorResource(R.color.orange_800_transparent_10)
            if (item.equalizerEntry.id == viewModel.currentEqualizerId) {
                selectedChip = chip
                selectedChipIndex = index
            }
            chip.setOnClickListener {
                viewModel.clearHistory()
                binding.undo.isEnabled = false
                viewModel.currentEqualizerId = it.tag as Long
                binding.presetTitleEdit.clearFocus()
                fillPreamp()
                fillBands()
                selectPreset()
                oldCurrentEqualizer = viewModel.getCurrentEqualizer()
            }
            binding.equalizerPresets.addView(chip)
        }

        binding.equalizerPresetsContainer.post {
            selectPreset()
            when {
                (selectedChipIndex == 0) -> binding.equalizerPresetsContainer.scrollTo(0, selectedChip!!.top)
                (selectedChipIndex == (viewModel.equalizerEntries.value?.size ?: 0) - 1) -> binding.equalizerPresetsContainer.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
                else -> binding.equalizerPresetsContainer.scrollTo(selectedChip!!.left - 48.dp, selectedChip!!.top)
            }
        }
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
        currentEqualizer.bands.sortedBy { it.index }.forEach { band ->
            val bandFrequency = MediaPlayer.Equalizer.getBandFrequency(band.index)
            val bar = EqualizerBar(requireContext(), bandFrequency)
            bar.setValue(band.bandValue)

            binding.equalizerBands.addView(bar)

            val params = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f)
            bar.layoutParams = params
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
        Settings.getInstance(requireActivity()).edit { putBoolean(KEY_EQUALIZER_ENABLED, isChecked) }
        binding.equalizerPresets.children.forEach {
            it.isEnabled = isChecked
        }
        val eqCardEnabled = isChecked && getEqualizerType() == TYPE_CUSTOM
        binding.equalizerPreamp.isEnabled = eqCardEnabled
        binding.equalizerBands.children.forEach {
            it.isEnabled = eqCardEnabled
        }
        binding.delete.isEnabled = isChecked
        binding.edit.isEnabled = isChecked
        binding.undo.isEnabled = eqCardEnabled && viewModel.history.isNotEmpty()
        binding.presetTitleEdit.isEnabled = eqCardEnabled
        binding.snapBands.isEnabled = eqCardEnabled
    }

    /**
     * Select the current preset and update the equalizer
     *
     */
    fun selectPreset() {
        updateEqualizer()
        if (!binding.presetTitleEdit.hasFocus()) binding.name = viewModel.getCurrentEqualizer().equalizerEntry.name
        binding.custom = getEqualizerType() == TYPE_CUSTOM
        val selectedChip = binding.equalizerPresets.findViewWithTag<Chip>(viewModel.getCurrentEqualizer().equalizerEntry.id)
        binding.equalizerPresets.check(selectedChip.id)
        updateEnabledState()
    }

    /**
     * Update the equalizer with the selected preset
     *
     */
    fun updateEqualizer(preventBarUpdate: Boolean = false) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        if (viewModel.bandCount == -1) viewModel.bandCount = withContext(Dispatchers.IO) {
            VLCInstance.getInstance(requireContext())
            MediaPlayer.Equalizer.getBandCount()
        }
        if (!preventBarUpdate) updateBars()
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
     * Band listener
     *
     * @property index the band index
     * @constructor Create empty Band listener
     */
    private inner class BandListener(private val index: Int) : OnEqualizerBarChangeListener {

        private var oldBands: MutableList<Int> = ArrayList()
        private var newBandList = ArrayList<EqualizerBand>()


        override fun onProgressChanged(value: Float, fromUser: Boolean) {
            if (!fromUser)
                return
            viewModel.saveInHistory(index)

            newBandList = ArrayList<EqualizerBand>()
            newBandList.add(viewModel.getCurrentEqualizer().bands.first { it.index ==  index}.copy(bandValue = value))
            if (!binding.equalizerButton.isChecked)
                binding.equalizerButton.isChecked = true

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

        }

        override fun onStartTrackingTouch() {
            oldBands.clear()
            for (eqBandsView in eqBandsViews) {
                oldBands.add(eqBandsView.getProgress())
            }
        }

        override fun onStopTrackingTouch() {
            oldBands.clear()
            viewModel.updateEqualizerBands(requireActivity(), newBandList)
        }
    }

}





