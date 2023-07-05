package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogAudioControlsSettingsBinding
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded

class AudioControlsSettingsDialog : VLCBottomSheetDialogFragment() {

    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = true

    private lateinit var binding: DialogAudioControlsSettingsBinding


    override fun initialFocusedView() = binding.fragmentContainerView.findViewById<View>(R.id.recycler_view) ?: binding.container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { if (requireActivity().showPinIfNeeded()) dismiss() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAudioControlsSettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
}
