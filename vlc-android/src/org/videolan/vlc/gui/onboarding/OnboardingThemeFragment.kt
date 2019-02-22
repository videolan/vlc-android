package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.videolan.vlc.R

class OnboardingThemeFragment : Fragment() {
    private lateinit var themeDescription: TextView
    private lateinit var lightThemeSelector: View
    private lateinit var darkThemeSelector: View
    private lateinit var dayNightTheme: View
    private var currentThemeIsLight = true


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_theme, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lightThemeSelector = view.findViewById(R.id.lightTheme)
        darkThemeSelector = view.findViewById(R.id.darkTheme)
        dayNightTheme = view.findViewById(R.id.dayNightTheme)
        themeDescription = view.findViewById(R.id.themeDescription)

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)


        val themeSelectorListener = View.OnClickListener {
            it.background = ContextCompat.getDrawable(requireActivity(), R.drawable.theme_selection_rounded)
            when (it) {
                lightThemeSelector -> {
                    darkThemeSelector.background = null
                    dayNightTheme.background = null
                    currentThemeIsLight = true
                    themeDescription.setText(R.string.light_theme)
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", false)
                            .putBoolean("daynight", false)
                            .apply()
                }
                darkThemeSelector -> {
                    themeDescription.setText(R.string.enable_black_theme)
                    lightThemeSelector.background = null
                    dayNightTheme.background = null
                    currentThemeIsLight = false
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", true)
                            .putBoolean("daynight", false)
                            .apply()
                }
                dayNightTheme -> {
                    themeDescription.setText(R.string.daynight_explanation)
                    lightThemeSelector.background = null
                    darkThemeSelector.background = null
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", false)
                            .putBoolean("daynight", true)
                            .apply()
                }
            }

        }

        lightThemeSelector.setOnClickListener(themeSelectorListener)
        darkThemeSelector.setOnClickListener(themeSelectorListener)
        dayNightTheme.setOnClickListener(themeSelectorListener)

    }

    companion object {
        fun newInstance(): OnboardingThemeFragment {
            return OnboardingThemeFragment()
        }
    }
}