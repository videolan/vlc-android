package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Switch
import androidx.fragment.app.Fragment
import org.videolan.vlc.R
import org.videolan.vlc.util.KEY_MEDIALIBRARY_SCAN
import org.videolan.vlc.util.ML_SCAN_OFF
import org.videolan.vlc.util.ML_SCAN_ON
import org.videolan.vlc.util.Settings

class OnboardingScanningFragment : Fragment() {
    private lateinit var scanningFolderCheckbox: CheckBox
    private lateinit var scanningEnableSwitch: Switch
    lateinit var onScanningCustomizeChangedListener: IOnScanningCustomizeChangedListener
    private lateinit var viewModel: OnboardingViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = getOnboardingModel()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_scanning, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scanningFolderCheckbox = view.findViewById(R.id.scanningFolderCheckbox)
        scanningEnableSwitch = view.findViewById(R.id.scanningEnableSwitch)
        super.onViewCreated(view, savedInstanceState)

    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)


        scanningEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            scanningFolderCheckbox.visibility = if (isChecked) View.VISIBLE else View.GONE
            val prefs = Settings.getInstance(requireActivity())
            prefs.edit().putInt(KEY_MEDIALIBRARY_SCAN, if (isChecked) ML_SCAN_ON else ML_SCAN_OFF).apply()
            viewModel.scanStorages = isChecked
            scanningFolderCheckbox.isChecked = false
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