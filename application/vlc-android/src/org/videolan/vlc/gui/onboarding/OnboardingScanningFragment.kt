package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.android.synthetic.main.onboarding_scanning.*
import org.videolan.tools.*
import org.videolan.vlc.R

class OnboardingScanningFragment : Fragment() {
    private val onScanningCustomizeChangedListener by lazy(LazyThreadSafetyMode.NONE) { requireActivity() as IOnScanningCustomizeChangedListener }
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val preferences by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(requireActivity()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_scanning, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        scanningEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            scanningFolderCheckbox.visibility = if (isChecked) View.VISIBLE else View.GONE
            autoScanningCheckbox.isChecked = isChecked
            preferences.putSingle(KEY_MEDIALIBRARY_SCAN, if (isChecked) ML_SCAN_ON else ML_SCAN_OFF)
            viewModel.scanStorages = isChecked
            scanningFolderCheckbox.isChecked = false
        }

        autoScanningCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferences.putSingle(KEY_MEDIALIBRARY_AUTO_RESCAN, isChecked)
        }

        scanningFolderCheckbox.isChecked = viewModel.customizeMediaFolders

        scanningFolderCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.customizeMediaFolders = isChecked
            onScanningCustomizeChangedListener.onCustomizedChanged(isChecked)
        }
    }

    companion object {
        fun newInstance() = OnboardingScanningFragment()
    }
}

interface IOnScanningCustomizeChangedListener {
    fun onCustomizedChanged(customizeEnabled: Boolean)
}
