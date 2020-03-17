package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.android.synthetic.main.onboarding_scanning.*
import org.videolan.tools.KEY_MEDIALIBRARY_SCAN
import org.videolan.tools.ML_SCAN_OFF
import org.videolan.tools.ML_SCAN_ON
import org.videolan.tools.Settings
import org.videolan.vlc.R

class OnboardingScanningFragment : Fragment() {
    lateinit var onScanningCustomizeChangedListener: IOnScanningCustomizeChangedListener
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val preferences by lazy { Settings.getInstance(requireActivity()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_scanning, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        scanningEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            scanningFolderCheckbox.visibility = if (isChecked) View.VISIBLE else View.GONE
            autoScanningCheckbox.isChecked = isChecked
            preferences.edit().putInt(KEY_MEDIALIBRARY_SCAN, if (isChecked) ML_SCAN_ON else ML_SCAN_OFF).apply()
            viewModel.scanStorages = isChecked
            scanningFolderCheckbox.isChecked = false
        }

        autoScanningCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("auto_rescan", isChecked).apply()
        }

        onScanningCustomizeChangedListener = requireActivity() as IOnScanningCustomizeChangedListener

        scanningFolderCheckbox.isChecked = viewModel.customizeMediaFolders

        scanningFolderCheckbox.setOnCheckedChangeListener { _, isChecked ->

            viewModel.customizeMediaFolders = isChecked

            if (::onScanningCustomizeChangedListener.isInitialized) {
                onScanningCustomizeChangedListener.onCustomizedChanged(isChecked)
            }
        }
    }

    companion object {
        fun newInstance(): OnboardingScanningFragment {
            return OnboardingScanningFragment()
        }
    }
}

interface IOnScanningCustomizeChangedListener {
    fun onCustomizedChanged(customizeEnabled: Boolean)
}
