package org.videolan.vlc

import org.hamcrest.Matchers.`is`

import android.content.res.Resources
import androidx.preference.Preference
import org.hamcrest.Description
import org.hamcrest.Matcher
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

    fun withSummary(resourceId: Int): Matcher<Preference> {
        return object : TypeSafeMatcher<Preference>() {
            private var resourceName: String? = null
            private var expectedText: String? = null

            override fun describeTo(description: Description) {
                description.appendText(" with summary string from resource id: ")
                description.appendValue(resourceId)
                if (null != resourceName) {
                    description.appendText("[")
                    description.appendText(resourceName)
                    description.appendText("]")
                }
                if (null != expectedText) {
                    description.appendText(" value: ")
                    description.appendText(expectedText)
                }
            }

            public override fun matchesSafely(preference: Preference): Boolean {
                if (null == expectedText) {
                    try {
                        expectedText = preference.context.resources.getString(resourceId)
                        resourceName = preference.context.resources.getResourceEntryName(resourceId)
                    } catch (ignored: Resources.NotFoundException) {
                        /* view could be from a context unaware of the resource id. */
                    }

                }
                return if (null != expectedText) {
                    expectedText == preference.summary.toString()
                } else {
                    false
                }
            }
        }
    }

    fun withSummaryText(summary: String): Matcher<Preference> {
        return withSummaryText(`is`(summary))
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

    fun withTitle(resourceId: Int): Matcher<Preference> {
        return object : TypeSafeMatcher<Preference>() {
            private var resourceName: String? = null
            private var expectedText: String? = null

            override fun describeTo(description: Description) {
                description.appendText(" with title string from resource id: ")
                description.appendValue(resourceId)
                if (null != resourceName) {
                    description.appendText("[")
                    description.appendText(resourceName)
                    description.appendText("]")
                }
                if (null != expectedText) {
                    description.appendText(" value: ")
                    description.appendText(expectedText)
                }
            }

            public override fun matchesSafely(preference: Preference): Boolean {
                if (null == expectedText) {
                    try {
                        expectedText = preference.context.resources.getString(resourceId)
                        resourceName = preference.context.resources.getResourceEntryName(resourceId)
                    } catch (ignored: Resources.NotFoundException) {
                        /* view could be from a context unaware of the resource id. */
                    }
                }
                return if (null != expectedText && preference.title != null) {
                    expectedText == preference.title.toString()
                } else {
                    false
                }
            }
        }
    }

    fun withTitleText(title: String): Matcher<Preference> {
        return withTitleText(`is`(title))
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
