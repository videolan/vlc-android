package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionListing
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.gui.view.ClickableSwitchPreference
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesExtensions : BasePreferenceFragment() {

    private var extensions: List<ExtensionListing> = ArrayList()
    private lateinit var settings: SharedPreferences
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.getInstance(requireContext())
        extensions = ExtensionsManager.getInstance().getExtensions(requireActivity().applicationContext, false)
        preferenceScreen = this.preferenceScreen
    }

    override fun onStart() {
        super.onStart()
        ((activity as PreferencesActivity).findViewById<View>(R.id.appbar) as AppBarLayout).setExpanded(true, false)
        createCheckboxes()
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen!!.removeAll()
    }

    override fun getXml(): Int {
        return R.xml.preferences_extensions
    }

    override fun getTitleId(): Int {
        return R.string.extensions_prefs_category
    }

    private fun createCheckboxes() {
        val pm = requireActivity().applicationContext.packageManager
        for (i in extensions.indices) {
            val extension = extensions[i]
            val switchPreference = ClickableSwitchPreference(preferenceScreen!!.context)
            switchPreference.title = extension.title()
            switchPreference.summary = extension.description()
            val key = ExtensionsManager.EXTENSION_PREFIX + "_" + extension.componentName().packageName
            switchPreference.key = key
            val iconRes = extension.menuIcon()
            var extensionIcon: Drawable? = null
            if (iconRes != 0) {
                try {
                    extensionIcon = ContextCompat.getDrawable(requireActivity(), extension.menuIcon())
                } catch (e: PackageManager.NameNotFoundException) {
                }

            }
            if (extensionIcon != null)
                switchPreference.icon = extensionIcon
            else
                try {
                    switchPreference.icon = pm.getApplicationIcon(extensions[i].componentName().packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    switchPreference.setIcon(R.drawable.icon)
                }

            val checked = settings.getBoolean(key, false)
            switchPreference.isChecked = checked
            preferenceScreen!!.addPreference(switchPreference)
            switchPreference.setOnSwitchClickListener(View.OnClickListener {
                if ((view as SwitchCompat).isChecked)
                    settings.putSingle(key, true)
                else
                    for ((key1) in settings.all)
                        if (key1.startsWith(ExtensionsManager.EXTENSION_PREFIX + "_"))
                            settings.putSingle(key1, false)
            })
            count++
        }

        if (count == 0) {
            val emptyCategory = PreferenceCategory(preferenceScreen!!.context)
            emptyCategory.setTitle(R.string.extensions_empty)
            preferenceScreen!!.addPreference(emptyCategory)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key
        if (key == null || !key.startsWith(ExtensionsManager.EXTENSION_PREFIX + "_"))
            return false
        val fragment = PreferencesExtensionFragment()
        fragment.arguments = bundleOf("extension_key" to key)
        loadFragment(fragment)
        return super.onPreferenceTreeClick(preference)
    }
}
