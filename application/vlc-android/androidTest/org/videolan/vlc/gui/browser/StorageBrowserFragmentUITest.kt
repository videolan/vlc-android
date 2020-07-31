package org.videolan.vlc.gui.browser

import android.content.Intent
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.contrib.RecyclerViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.BaseUITest
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.*
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class StorageBrowserFragmentUITest : BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(SecondaryActivity::class.java, true, false)

    lateinit var activity: SecondaryActivity

    override fun beforeTest() {
        val intent = Intent().apply {
            putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.STORAGE_BROWSER)
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
    }

    private fun testRecyclerViewShownAndSizeGreaterThanSize(minSize: Int) {
        onView(withId(R.id.network_list))
                .check(matches(isDisplayed()))
                .check(matches(sizeOfAtLeast(minSize)))
    }

    @Test
    fun whenAtRoot_checkCorrectAppbar() {
        onView(withId(R.id.main_toolbar))
                .check(matches(
                        hasDescendant(withText(R.string.directories_summary))
                ))

        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.add_custom_path))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtRootClickAddCustomPath_showDialog() {
        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.add_custom_path))
                .inRoot(isPlatformPopup())
                .perform(click())
    }

    @Test
    fun whenAtRoot_checkInternalStorageShown() {
        testRecyclerViewShownAndSizeGreaterThanSize(1)

        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(rvMatcher.atPosition(0))
                .check(matches(hasDescendant(withText(R.string.internal_memory))))
    }

    @Test
    fun whenAtRoot_togglingInternalStorageWorks() {
        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(allOf(
                withId(R.id.browser_checkbox), isDescendantOfA(rvMatcher.atPosition(0))
        ))
                .check(matches(withCheckBoxState(equalTo(ThreeStatesCheckbox.STATE_UNCHECKED))))
                .perform(click())
                .check(matches(withCheckBoxState(equalTo(ThreeStatesCheckbox.STATE_CHECKED))))
    }
}