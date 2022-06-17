package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import org.videolan.resources.AndroidDevices
import org.videolan.vlc.R

class OnboardingThemeFragment : OnboardingFragment(), View.OnClickListener {

    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var titleView: TextView
    private lateinit var themeDescription: TextView
    private lateinit var lightTheme: View
    private lateinit var darkTheme: View
    private lateinit var dayNightTheme: View
    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_theme, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.theme_title)
        themeDescription = view.findViewById(R.id.themeDescription)
        lightTheme = view.findViewById<TextView>(R.id.lightTheme)
        darkTheme = view.findViewById<TextView>(R.id.darkTheme)
        dayNightTheme = view.findViewById<TextView>(R.id.dayNightTheme)
        themeDescription.setText(if (AndroidDevices.canUseSystemNightMode())R.string.daynight_system_explanation else R.string.daynight_legacy_explanation)
        lightTheme.setOnClickListener(this)
        darkTheme.setOnClickListener(this)
        dayNightTheme.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        view.background = ContextCompat.getDrawable(requireActivity(), R.drawable.theme_selection_rounded)
        view.animate().scaleX(1F).scaleY(1F)
        when (view) {
            lightTheme -> {
                darkTheme.background = null
                dayNightTheme.background = null
                darkTheme.animate().scaleX(0.8F).scaleY(0.8F)
                dayNightTheme.animate().scaleX(0.8F).scaleY(0.8F)
                viewModel.theme = AppCompatDelegate.MODE_NIGHT_NO
                themeDescription.setText(R.string.light_theme)
            }
            darkTheme -> {
                themeDescription.setText(R.string.enable_black_theme)
                lightTheme.background = null
                dayNightTheme.background = null
                lightTheme.animate().scaleX(0.8F).scaleY(0.8F)
                dayNightTheme.animate().scaleX(0.8F).scaleY(0.8F)
                viewModel.theme = AppCompatDelegate.MODE_NIGHT_YES
            }
            dayNightTheme -> {
                themeDescription.setText(if (AndroidDevices.canUseSystemNightMode())R.string.daynight_system_explanation else R.string.daynight_legacy_explanation)
                lightTheme.background = null
                darkTheme.background = null
                lightTheme.animate().scaleX(0.8F).scaleY(0.8F)
                darkTheme.animate().scaleX(0.8F).scaleY(0.8F)
                viewModel.theme = if (AndroidDevices.canUseSystemNightMode()) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO
            }
        }
    }

    companion object {
        fun newInstance() = OnboardingThemeFragment()
    }
}
