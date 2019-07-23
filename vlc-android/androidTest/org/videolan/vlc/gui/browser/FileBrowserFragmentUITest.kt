package org.videolan.vlc.gui.browser

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.RootMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.*
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.util.EXTRA_TARGET
import java.lang.Thread.sleep

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FileBrowserFragmentUITest : BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(MainActivity::class.java, true, false)

    lateinit var activity: MainActivity

    @Before
    fun init() {
        val intent = Intent().apply {
            putExtra(EXTRA_TARGET, R.id.nav_directories)
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
    fun whenAtRoot_checkCorrectAppbarTitle() {
        onView(withId(R.id.main_toolbar))
                .check(matches(
                        hasDescendant(withText(R.string.directories))
                ))
    }

    @Test
    fun whenAtRoot_checkInternalStorageShown() {
        testRecyclerViewShownAndSizeGreaterThanSize(1)

        val rvMatcher = withRecyclerView(R.id.network_list)
        // Shows Dummy category
        onView(rvMatcher.atPositionOnView(0, R.id.separator_title))
                .check(matches(withText(context.getString(R.string.browser_storages))))
        onView(rvMatcher.atPosition(1))
                .check(matches(hasDescendant(withText(R.string.internal_memory))))
    }

    @Test
    fun whenAtRoot_checkQuickAccessShown() {
        testRecyclerViewShownAndSizeGreaterThanSize(3)

        onView(withRecyclerView(R.id.network_list).atPositionOnView(2, R.id.separator_title))
                .check(matches(withText(R.string.browser_quick_access)))
    }

    @Test
    fun whenAtInternalStorage_checkCorrectAppbarTitle() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        onView(withId(R.id.main_toolbar))
            .check(matches(
                    hasDescendant(withText(R.string.internal_memory))
            ))
    }

    @Test
    fun whenAtRoot_checkLongPressOfInternalStorageUpdatesBackgroundAndAppbar() {
        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(rvMatcher.atPosition(1)).perform(longClick())

        onView(rvMatcher.atPosition(1))
                .check(matches(withBgColor(context.getColor(R.color.orange200transparent))))

        onView(withId(R.id.action_mode_file_play))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_delete))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_add_playlist))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtInternalStorage_checkActionMode() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        onView(withId(R.id.ml_menu_filter))
                .check(matches(isDisplayed()))
        onView(withId(R.id.ml_menu_save))
                .check(matches(isDisplayed()))

        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.refresh))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtInternalStorage_checkSortMethods() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        openActionBarOverflowOrOptionsMenu(context)

        onView(anyOf(withText(R.string.sortby), withId(R.id.ml_menu_sortby)))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
                .perform(click())

        onView(anyOf(withText(R.string.sortby_name), withId(R.id.ml_menu_sortby_name)))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
        onView(anyOf(withText(R.string.sortby_filename), withId(R.id.ml_menu_sortby_filename)))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtRoot_checkLongPressAndAddToPlaylistOpenPlaylistBottomSheet() {
        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(rvMatcher.atPosition(1)).perform(longClick())

        openActionBarOverflowOrOptionsMenu(context)

        assertThat(activity.supportFragmentManager.findFragmentByTag("fragment_add_to_playlist"), notNullValue())
    }

    @Test
    fun whenAtRoot_checkOverflowMenuShowsRefresh() {
        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.refresh))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtRoot_clickContextMenuToOpenContextBottomSheet() {
        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(rvMatcher.atPositionOnView(1, R.id.item_more)).perform(click())
        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())

        onView(withId(R.id.ctx_title))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.internal_memory)))
        onView(withId(R.id.ctx_list))
                .check(matches(isDisplayed()))
                .check(matches(sizeOfAtLeast(2)))
    }

    @Test
    fun whenAtRoot_addInternalStorageToFavoriteAndCheckListUpdated() {
        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(rvMatcher.atPosition(1))
                .check(matches(isDisplayed()))

        val oldCount = rvMatcher.recyclerView?.adapter?.itemCount ?: 0

        onView(rvMatcher.atPositionOnView(1, R.id.item_more))
                .perform(click())

        onView(withRecyclerView(R.id.ctx_list).atPosition(1))
                .check(matches(hasDescendant(withText(R.string.favorites_add))))
                .perform(click())

        onView(withId(R.id.network_list))
                .check(matches(sizeOfAtLeast(oldCount + 1)))
    }

    private fun sizeOfAtLeast(minSize: Int): Matcher<in View> {
        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Recycler view has atleast $minSize items")
            }

            override fun matchesSafely(view: View): Boolean {
                return view is RecyclerView && (view.adapter?.itemCount ?: 0) >= minSize
            }
        }
    }
}