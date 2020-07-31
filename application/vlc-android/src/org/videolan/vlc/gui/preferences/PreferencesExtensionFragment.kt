package org.videolan.vlc.gui.preferences


import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionListing
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesExtensionFragment : BasePreferenceFragment() {

    private var extension: ExtensionListing? = null
    private var extensionId: Int = 0
    private var extensionTitle: String? = null
    private var extensionKey: String? = null
    private var extensionPackageName: String? = null
    private lateinit var settings: SharedPreferences
    // private var preferenceScreen: PreferenceScreen? = null
    private var androidAutoAvailable = false
    private val preferences = ArrayList<Preference>()

    override fun onCreate(bundle: Bundle?) {
        var newBundle = bundle
        super.onCreate(newBundle)
        settings = Settings.getInstance(requireActivity())
        setHasOptionsMenu(true)
        if (newBundle == null)
            newBundle = arguments

        if (newBundle != null) {
            extensionKey = newBundle.getString("extension_key")
            extensionPackageName = extensionKey!!.replace(ExtensionsManager.EXTENSION_PREFIX + "_", "")
            extensionId = ExtensionsManager.getInstance().getExtensionId(extensionPackageName)
            extension = ExtensionsManager.getInstance().getExtensions(activity!!.application, false)[extensionId]
            extensionTitle = extension!!.title()
            setTitle(extensionTitle)
            androidAutoAvailable = ExtensionsManager.androidAutoInstalled && extension!!.androidAutoEnabled()
            createCheckboxes()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("extension_key", extensionKey)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        ((activity as PreferencesActivity).findViewById<View>(R.id.appbar) as AppBarLayout).setExpanded(true, false)
    }

    override fun getXml(): Int {
        return R.xml.preferences_extension_page
    }

    override fun getTitleId(): Int {
        return 0
    }

    private fun setTitle(title: String?) {
        val activity = activity as PreferencesActivity?
        if (activity != null && activity.supportActionBar != null)
            (getActivity() as PreferencesActivity).supportActionBar!!.title = title
    }

    private fun createCheckboxes() {
        preferenceScreen = this.preferenceScreen

        //Main switch
        val switchPreference = SwitchPreferenceCompat(preferenceScreen!!.context)
        switchPreference.title = preferenceScreen!!.context.getString(R.string.extension_prefs_activation_title).toUpperCase()
        switchPreference.key = extensionKey
        switchPreference.isChecked = settings.getBoolean(extensionKey, false)
        switchPreference.onPreferenceChangeListener = null
        preferenceScreen!!.addPreference(switchPreference)

        //Android-auto
        if (androidAutoAvailable) {
            val checkbox = CheckBoxPreference(preferenceScreen!!.context)
            checkbox.setTitle(R.string.android_auto)
            val key = extensionKey + "_" + ExtensionsManager.ANDROID_AUTO_SUFFIX
            checkbox.key = key
            checkbox.isChecked = switchPreference.isChecked && settings.getBoolean(key, false)
            checkbox.isEnabled = switchPreference.isChecked
            preferences.add(checkbox)
            preferenceScreen!!.addPreference(checkbox)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key
        if (key == null || !key.startsWith(extensionKey!!))
            return false

        if (key == extensionKey) {
            val switchPreference = preference as CheckBoxPreference
            settings.putSingle(key, switchPreference.isChecked)
            if (switchPreference.isChecked) {
                for (checkbox in preferences)
                    checkbox.isEnabled = true
            } else {
                for (checkbox in preferences) {
                    (checkbox as CheckBoxPreference).isChecked = false
                    settings.putSingle(checkbox.getKey(), false)
                    checkbox.setEnabled(false)
                }
            }
        } else if (key.endsWith("_" + ExtensionsManager.ANDROID_AUTO_SUFFIX)) {
            settings.putSingle(preference.key, (preference as TwoStatePreference).isChecked)
        }
        return super.onPreferenceTreeClick(preference)
    }
}