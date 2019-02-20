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
    private lateinit var themeDayNightDescription: TextView
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
        themeDayNightDescription = view.findViewById(R.id.themeDayNightDescription)

    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        themeDayNightDescription.scaleX = 0f
        themeDayNightDescription.scaleY = 0f
        themeDayNightDescription.alpha = 0f

        val themeSelectorListener = View.OnClickListener {
            it.background = ContextCompat.getDrawable(requireActivity(), R.drawable.theme_selection_rounded)
            when (it) {
                lightThemeSelector -> {
                    darkThemeSelector.background = null
                    dayNightTheme.background = null
                    currentThemeIsLight = true
                    themeDayNightDescription.animate().scaleY(0f).scaleX(0f).alpha(0f)
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", false)
                            .putBoolean("daynight", false)
                            .apply()
                    //todo effectively apply it to any launched UI
//                    requireActivity().setResult(PreferencesActivity.RESULT_RESTART)
                }
                darkThemeSelector -> {
                    lightThemeSelector.background = null
                    dayNightTheme.background = null
                    currentThemeIsLight = false
                    themeDayNightDescription.animate().scaleY(0f).scaleX(0f).alpha(0f)
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", true)
                            .putBoolean("daynight", false)
                            .apply()
                    //todo effectively apply it to any launched UI
//                    requireActivity().setResult(PreferencesActivity.RESULT_RESTART)
                }
                dayNightTheme -> {
                    lightThemeSelector.background = null
                    darkThemeSelector.background = null
                    themeDayNightDescription.animate().scaleY(1f).scaleX(1f).alpha(1f)
                    PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit()
                            .putBoolean("enable_black_theme", false)
                            .putBoolean("daynight", true)
                            .apply()
                    //todo effectively apply it to any launched UI
//                    requireActivity().setResult(PreferencesActivity.RESULT_RESTART)
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