package org.videolan.vlc.gui.onboarding

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.onboarding_theme.*
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices

class OnboardingThemeFragment : Fragment(), CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private lateinit var viewModel: OnboardingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = getOnboardingModel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_theme, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) follow_system_switch.visibility = View.VISIBLE

        lightTheme.setOnClickListener(this)
        darkTheme.setOnClickListener(this)
        dayNightTheme.setOnClickListener(this)
        follow_system_switch.isChecked = AndroidDevices.canUseSystemNightMode()
        follow_system_switch.setOnCheckedChangeListener(this)
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView) {
            follow_system_switch -> viewModel.theme = if (isChecked) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO
        }
    }

    override fun onClick(view: View) {
        view.background = ContextCompat.getDrawable(requireActivity(), R.drawable.theme_selection_rounded)
        when (view) {
            lightTheme -> {
                darkTheme.background = null
                dayNightTheme.background = null
                viewModel.theme = AppCompatDelegate.MODE_NIGHT_NO
                themeDescription.setText(R.string.light_theme)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) follow_system_switch.visibility = View.GONE
            }
            darkTheme -> {
                themeDescription.setText(R.string.enable_black_theme)
                lightTheme.background = null
                dayNightTheme.background = null
                viewModel.theme = AppCompatDelegate.MODE_NIGHT_YES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) follow_system_switch.visibility = View.GONE
            }
            dayNightTheme -> {
                themeDescription.setText(R.string.daynight_explanation)
                lightTheme.background = null
                darkTheme.background = null
                viewModel.theme = AppCompatDelegate.MODE_NIGHT_AUTO
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) follow_system_switch.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        fun newInstance() = OnboardingThemeFragment()
    }
}