package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf
import org.videolan.tools.Settings
import org.videolan.vlc.BaseUITest
import org.videolan.vlc.PreferenceMatchers
import org.videolan.vlc.R
import org.videolan.vlc.onPreferenceRow

abstract class BasePreferenceUITest : BaseUITest() {
    val settings: SharedPreferences = Settings.getInstance(context)

    private fun checkListPreferenceValidSelected(@StringRes textRes: Int) {
        Espresso.onView(ViewMatchers.withText(textRes))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
    }

    private fun checkListPreferenceValidSelected(text: String) {
        Espresso.onView(ViewMatchers.withText(text))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
    }

    fun checkModeChanged(key: String, mode: String, defValue: String, map: Map<String, *>) {
        val oldMode = settings.getString(key, defValue) ?: defValue

        onPreferenceRow(R.id.recycler_view, PreferenceMatchers.withKey(key))!!
                .perform(ViewActions.click())

        val oldVal = map.getValue(oldMode).toString()
        oldVal.toIntOrNull()?.let {
            checkListPreferenceValidSelected(it)
        } ?: checkListPreferenceValidSelected(oldVal)

        val newVal = map.getValue(mode).toString()
        val matcher = newVal.toIntOrNull()?.let {
            ViewMatchers.withText(it)
        } ?: ViewMatchers.withText(newVal)
        Espresso.onView(allOf(ViewMatchers.isAssignableFrom(AppCompatCheckedTextView::class.java), matcher))
                .perform(ViewActions.click())

        ViewMatchers.assertThat(settings.getString(key, defValue), Matchers.`is`(mode))
    }

    fun checkToggleWorks(key: String, settings: SharedPreferences, default: Boolean = true) {
        val oldValue = settings.getBoolean(key, default)
        onPreferenceRow(R.id.recycler_view, PreferenceMatchers.withKey(key))!!
                .check(ViewAssertions.matches(ViewMatchers.hasDescendant(if (oldValue) ViewMatchers.isChecked() else ViewMatchers.isNotChecked())))
                .perform(ViewActions.click())

        val newValue = settings.getBoolean(key, true)
        ViewMatchers.assertThat("'$key' setting didn't update", newValue, Matchers.not(Matchers.equalTo(oldValue)))
    }
}