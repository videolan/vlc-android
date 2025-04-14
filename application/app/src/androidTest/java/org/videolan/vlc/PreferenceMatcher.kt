package org.videolan.vlc

import androidx.preference.Preference
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.hamcrest.TypeSafeMatcher

/** A collection of hamcrest matchers that match [Preference]s.
 *  These match with the [androidx.preference.Preference] class from androidX APIs.
 **/
object PreferenceMatchers {

    val isEnabled: Matcher<Preference>
        get() = object : TypeSafeMatcher<Preference>() {
            override fun describeTo(description: Description) {
                description.appendText(" is an enabled preference")
            }

            public override fun matchesSafely(pref: Preference): Boolean {
                return pref.isEnabled
            }
        }

    fun withSummaryText(summaryMatcher: Matcher<String>): Matcher<Preference> {
        return object : TypeSafeMatcher<Preference>() {
            override fun describeTo(description: Description) {
                description.appendText(" a preference with summary matching: ")
                summaryMatcher.describeTo(description)
            }

            public override fun matchesSafely(pref: Preference): Boolean {
                val summary = pref.summary.toString()
                return summaryMatcher.matches(summary)
            }
        }
    }

    fun withTitleText(titleMatcher: Matcher<String>): Matcher<Preference> {
        return object : TypeSafeMatcher<Preference>() {
            override fun describeTo(description: Description) {
                description.appendText(" a preference with title matching: ")
                titleMatcher.describeTo(description)
            }

            public override fun matchesSafely(pref: Preference): Boolean {
                if (pref.title == null) {
                    return false
                }
                val title = pref.title.toString()
                return titleMatcher.matches(title)
            }
        }
    }

    fun withKey(key: String): Matcher<Preference> {
        return withKey(`is`(key))
    }

    fun withKey(keyMatcher: Matcher<String>): Matcher<Preference> {
        return object : TypeSafeMatcher<Preference>() {
            override fun describeTo(description: Description) {
                description.appendText(" preference with key matching: ")
                keyMatcher.describeTo(description)
            }

            public override fun matchesSafely(pref: Preference): Boolean {
                return keyMatcher.matches(pref.key)
            }
        }
    }
}
