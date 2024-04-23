package org.videolan.vlc.gui.browser

import android.content.Intent
import android.widget.AutoCompleteTextView
import androidx.core.content.edit
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.EXTRA_TARGET
import org.videolan.tools.Settings
import org.videolan.vlc.*
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.SelectorViewHolder

class FileBrowserFragmentUITest : BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(MainActivity::class.java, true, false)

    lateinit var activity: MainActivity

    override fun beforeTest() {
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

        onView(isRoot()).perform(orientationPortrait())

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

        onView(isRoot()).perform(orientationLandscape())

        onView(anyOf(withContentDescription(R.string.sortby), withId(R.id.ml_menu_sortby)))
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

    @Test
    fun whenAtInternalStorage_checkMultipleSelectionWorks() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(rvMatcher.atPosition(0)).perform(longClick())
        onView(rvMatcher.atPosition(2)).perform(longClick())

        onView(rvMatcher.atPosition(0)).check(matches(withBgColor(context.getColor(R.color.orange200transparent))))
        onView(rvMatcher.atPosition(2)).check(matches(withBgColor(context.getColor(R.color.orange200transparent))))
    }

    @Test
    fun whenAtInternalStorageAndFolderLongPress_checkAppbar() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        val rvMatcher = MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list)

        onView(allOf(
                rvMatcher.atGivenType(MediaWrapper.TYPE_DIR), firstView()
        )).perform(longClick())

        onView(withId(R.id.action_mode_file_play))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_delete))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_add_playlist))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtBrowserFolderClickMore_checkContextMenu() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        val rvMatcher = MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list)

        onView(allOf(
                withId(R.id.item_more), isDescendantOfA(rvMatcher.atGivenType(MediaWrapper.TYPE_DIR)), firstView()
        )).perform(click())

        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())

        onView(withId(R.id.ctx_list))
                .check(matches(isDisplayed()))
                .check(matches(withCount(equalTo(3))))
                .check(matches(hasDescendant(withText(R.string.play))))
                .check(matches(hasDescendant(withText(R.string.favorites_add))))
                .check(matches(hasDescendant(withText(R.string.delete))))
    }

    @Test
    fun whenAtMovieFolderAndVideoLongPress_checkAppbar() {
        onView(allOf(
                MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_DIR), hasDescendant(withText(containsString("Video")))
        )).perform(click())

        onView(allOf(
            MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_VIDEO), firstView()
        )).perform(longClick())

        onView(withId(R.id.action_mode_file_info))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_play))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_delete))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_file_add_playlist))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtMovieFolderAndVideoClickMore_checkContextMenu() {
        onView(allOf(
                MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_DIR), hasDescendant(withText(containsString("Video")))
        )).perform(click())

        onView(allOf(
                withId(R.id.item_more), isDescendantOfA(MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_VIDEO)), firstView()
        )).perform(click())

        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())

        onView(withId(R.id.ctx_list))
                .check(matches(isDisplayed()))
                .check(matches(withCount(equalTo(7))))
                .check(matches(hasDescendant(withText(R.string.play_all))))
                .check(matches(hasDescendant(withText(R.string.play_as_audio))))
                .check(matches(hasDescendant(withText(R.string.append))))
                .check(matches(hasDescendant(withText(R.string.info))))
                .check(matches(hasDescendant(withText(R.string.download_subtitles))))
                .check(matches(hasDescendant(withText(R.string.add_to_playlist))))
                .check(matches(hasDescendant(withText(R.string.delete))))
    }

    @Test
    fun whenAtInternalStorageAndContainsUnknownFile_checkShownIfSettingIsTrue() {
        Settings.getInstance(context).edit(commit = true) { putBoolean("browser_show_all_files", true) }

        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(rvMatcher.atPosition(0))
                .check(matches(isDisplayed()))

        val adapter = rvMatcher.recyclerView?.adapter as? DiffUtilAdapter<MediaLibraryItem, RecyclerView.ViewHolder>
        val pos = adapter?.let { findFirstPosition(it, withMediaType(MediaWrapper.TYPE_ALL)) } ?: -1

        assertThat(pos, not(equalTo(-1)))

        // TODO: Fails because it doesn't scroll completely as required
        onView(withId(R.id.network_list))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(pos, longClick())) // FAILED

        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())
    }

    @Test
    fun whenAtInternalStorageAndContainsUnknownFile_checkNotShownIfSettingIsFalse() {
        // TODO: Fails, because this preference value doesn't get reflected in the provider
        Settings.getInstance(context).edit(commit = true) { putBoolean("browser_show_all_files", false) }

        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())

        val rvMatcher = withRecyclerView(R.id.network_list)
        onView(rvMatcher.atPosition(0))
                .check(matches(isDisplayed()))

        val adapter = rvMatcher.recyclerView?.adapter as? DiffUtilAdapter<MediaLibraryItem, RecyclerView.ViewHolder>
        val pos = adapter?.let { findFirstPosition(adapter, withMediaType(MediaWrapper.TYPE_ALL)) } ?: -1

        assertThat(pos, equalTo(-1)) // FAILED
    }

    @Test
    fun whenAtMusicFolderAndAudioClickMore_checkContextMenu() {
        onView(allOf(
                MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_DIR), hasDescendant(withText(containsString("Music")))
        )).perform(click())

        onView(allOf(
                withId(R.id.item_more), isDescendantOfA(MediaRecyclerViewMatcher<SelectorViewHolder<ViewDataBinding>>(R.id.network_list).atGivenType(MediaWrapper.TYPE_AUDIO)), firstView()
        )).perform(click())

        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())

        onView(withId(R.id.ctx_list))
                .check(matches(isDisplayed()))
                .check(matches(sizeOfAtLeast(4)))
                .check(matches(hasDescendant(withText(R.string.play_all))))
                .check(matches(hasDescendant(withText(R.string.append))))
                .check(matches(hasDescendant(withText(R.string.info))))
                .check(matches(hasDescendant(withText(R.string.add_to_playlist))))
                .check(matches(hasDescendant(withText(R.string.delete))))
    }

    @Test
    fun whenAtRootAndClickedOnItemIcon_multiSelectionModeIsToggled() {
        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(allOf(
                withId(R.id.item_icon), isDescendantOfA(rvMatcher.atPosition(1))
        )).perform(click())
        onView(rvMatcher.atPosition(3)).perform(click())

        onView(rvMatcher.atPosition(1)).check(matches(withBgColor(context.getColor(R.color.orange200transparent))))
        onView(rvMatcher.atPosition(3)).check(matches(withBgColor(context.getColor(R.color.orange200transparent))))

        onView(rvMatcher.atPosition(3)).perform(click())
        onView(rvMatcher.atPosition(1)).perform(click())

        onView(rvMatcher.atPosition(1)).check(matches(not(withBgColor(context.getColor(R.color.orange200transparent)))))
        onView(rvMatcher.atPosition(3)).check(matches(not(withBgColor(context.getColor(R.color.orange200transparent)))))
    }

    @Test
    fun whenAtSomeFolderAndFavorited_checkQuickAccessIsUpdatedAtRoot() {
        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(rvMatcher.atPosition(1))
                .check(matches(isDisplayed()))

        val oldCount = rvMatcher.recyclerView?.adapter?.itemCount ?: 0

        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())
        onView(withRecyclerView(R.id.network_list).atPosition(0)).perform(click())

        onView(withId(R.id.ml_menu_save))
                .check(matches(withActionIconDrawable(R.drawable.ic_fav_add)))
                .perform(click())
                .check(matches(withActionIconDrawable(R.drawable.ic_fav_remove)))

        onView(isRoot()).perform(pressBack())
        onView(isRoot()).perform(pressBack())

        onView(withId(R.id.network_list))
                .check(matches(withCount(equalTo(oldCount + 1))))
    }

    @Test
    fun whenAtSomeQuickAccessFolderAndDefavorited_checkQuickAccessIsUpdatedAtRoot() {
        val rvMatcher = withRecyclerView(R.id.network_list)

        onView(rvMatcher.atPosition(1))
                .check(matches(isDisplayed()))

        val oldCount = rvMatcher.recyclerView?.adapter?.itemCount ?: 0

        onView(withRecyclerView(R.id.network_list).atPosition(3)).perform(click())

        onView(withId(R.id.ml_menu_save))
                .check(matches(withActionIconDrawable(R.drawable.ic_fav_remove)))
                .perform(click())
                .check(matches(withActionIconDrawable(R.drawable.ic_fav_add)))

        onView(isRoot()).perform(pressBack())

        onView(withId(R.id.network_list))
                .check(matches(withCount(equalTo(oldCount - 1))))
    }

    @Test
    fun whenAtSomeFolderAndFiltered_checkItemsAreFiltered() {
        onView(withRecyclerView(R.id.network_list).atPosition(1)).perform(click())
        onView(withId(R.id.ml_menu_filter))
                .perform(click())

        onView(isAssignableFrom(AutoCompleteTextView::class.java))
                .perform(typeTextIntoFocusedView("anasjfd"))

        onView(withId(R.id.network_list))
                .check(matches(withCount(equalTo(0))))

        onView(isAssignableFrom(AutoCompleteTextView::class.java))
                .perform(clearText())
                .perform(typeTextIntoFocusedView("Music"))

        onView(withId(R.id.network_list))
                .check(matches(sizeOfAtLeast(1)))
    }
}