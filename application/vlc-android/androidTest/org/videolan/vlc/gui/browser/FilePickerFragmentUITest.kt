package org.videolan.vlc.gui.browser

import androidx.test.rule.ActivityTestRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.BaseUITest
import org.videolan.vlc.R
import org.videolan.vlc.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FilePickerFragmentUITest : BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(FilePickerActivity::class.java)

    lateinit var activity: FilePickerActivity

    override fun beforeTest() {
        activity = activityTestRule.activity
    }

    @Test
    fun whenAtSomeFolder_clickOnHomeIconReturnsBackToRoot() {
        onView(withRecyclerView(R.id.network_list).atPosition(0)).perform(click())
        onView(withRecyclerView(R.id.network_list).atPosition(0)).perform(click())

        onView(withId(R.id.network_list)).check(matches(withCount(greaterThan(2))))

        onView(withId(R.id.button_home)).perform(click())

        onView(withId(R.id.network_list)).check(matches(withCount(equalTo(1))))
    }
}
