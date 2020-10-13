package org.videolan.vlc.gui

import android.content.Intent
import android.widget.EditText
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import com.google.android.material.internal.NavigationMenuItemView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.vlc.*
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.tools.CoroutineContextProvider
import org.videolan.resources.EXTRA_TARGET
import org.videolan.vlc.util.TestCoroutineContextProvider

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlaylistFragmentUITest: BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(MainActivity::class.java, true, false)

    lateinit var activity: MainActivity

    override fun beforeTest() {
        SavePlaylistDialog.overrideCreator = false
        SavePlaylistDialog.registerCreator(clazz = CoroutineContextProvider::class.java) { TestCoroutineContextProvider() }

        val intent = Intent().apply {
            putExtra(EXTRA_TARGET, R.id.nav_playlists)
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
    }

    @After
    fun resetData() {
        Medialibrary.getInstance().playlists.map { it.delete() }
    }

    private fun createDummyPlaylist() {
        val ml = Medialibrary.getInstance()
        val pl = ml.createPlaylist(DUMMY_PLAYLIST)
        pl.append(ml.getPagedVideos(Medialibrary.SORT_DEFAULT, false, 5, 0).map { it.id })
        pl.append(ml.getPagedAudio(Medialibrary.SORT_DEFAULT, false, 5, 0).map { it.id })
    }

    @Test
    fun whenNoMedia_checkListEmpty() {
        onView(withId(R.id.audio_list))
                .check(matches(withCount(equalTo(0))))
    }

    @Test
    fun whenPlaylistAddedFromDirectoriesView_checkPlaylistUpdated() {
        // Navigate to directories view
//        onView(withId(R.id.root_container))
//                .perform(open())

        onView(allOf(instanceOf(NavigationMenuItemView::class.java), hasDescendant(withText(R.string.directories))))
                .check(matches(isDisplayed()))
                .perform(click())

        // Add Internal Storage to playlist
        onView(withRecyclerView(R.id.network_list).atPosition(3))
                .perform(longClick())

        openActionBarOverflowOrOptionsMenu(context)

        onView(allOf(isDescendantOfA(withId(R.id.dialog_playlist_name)), instanceOf(EditText::class.java)))
                .perform(click(), typeTextIntoFocusedView("storage"))
        onView(withId(R.id.dialog_playlist_save))
                .perform(click())

        // Navigate back to playlists view
//        onView(withId(R.id.root_container))
//                .perform(open())

        onView(allOf(instanceOf(NavigationMenuItemView::class.java), hasDescendant(withText(R.string.playlists))))
                .check(matches(isDisplayed()))
                .perform(click())

        onView(withId(R.id.audio_list))
                .check(matches(withCount(equalTo(1))))
        onView(withRecyclerView(R.id.audio_list).atPosition(0))
                .check(matches(hasDescendant(withText("storage"))))
    }

    @Test
    fun whenOnePlaylist_checkCardDetails() {
        createDummyPlaylist()
        Thread.sleep(2000)

        onView(withId(R.id.audio_list))
                .check(matches(sizeOfAtLeast(1)))

        // Section Header should be T
//        onView(withId(R.id.section_header))
//                .check(matches(withText(DUMMY_PLAYLIST[0].toString().toUpperCase())))

        val rvMatcher = withRecyclerView(R.id.audio_list)
        onView(rvMatcher.atPositionOnView(0, R.id.title))
                .check(matches(withText(DUMMY_PLAYLIST)))

        onView(rvMatcher.atPositionOnView(0, R.id.subtitle))
                .check(matches(withText("1 tracks")))
    }

    @Test
    fun whenOnePlaylist_checkContextMenuWorks() {
        createDummyPlaylist()
        Thread.sleep(1500)

        onView(withId(R.id.audio_list))
                .check(matches(sizeOfAtLeast(1)))

        val rvMatcher = withRecyclerView(R.id.audio_list)
        onView(rvMatcher.atPositionOnView(0, R.id.item_more))
                .perform(click())

        assertThat(activity.supportFragmentManager.findFragmentByTag("context"), notNullValue())

        onView(withId(R.id.ctx_list))
                .check(matches(isDisplayed()))
                .check(matches(sizeOfAtLeast(5)))
                .check(matches(hasDescendant(withText(R.string.play))))
                .check(matches(hasDescendant(withText(R.string.append))))
                .check(matches(hasDescendant(withText(R.string.insert_next))))
                .check(matches(hasDescendant(withText(R.string.add_to_playlist))))
                .check(matches(hasDescendant(withText(R.string.delete))))
    }

    @Test
    fun whenOnePlaylist_checkLongPressWorks() {
        createDummyPlaylist()
        Thread.sleep(1500)

        onView(withId(R.id.audio_list))
                .check(matches(sizeOfAtLeast(1)))

        val rvMatcher = withRecyclerView(R.id.audio_list)
        onView(rvMatcher.atPosition(0))
                .perform(longClick())

        onView(withId(R.id.action_mode_audio_play))
                .check(matches(isDisplayed()))
        onView(withId(R.id.action_mode_audio_info))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtRoot_checkAppbarWorks() {
        onView(withId(R.id.ml_menu_filter))
                .check(matches(isDisplayed()))

        // Check sort works
        onView(withId(R.id.ml_menu_sortby))
                .check(matches(isDisplayed()))
                .perform(click())

        onView(anyOf(withText(R.string.sortby_name), withId(R.id.ml_menu_sortby_name)))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))

        Espresso.pressBack()

        // Check overflow menu works
        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.refresh))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))

        Espresso.pressBack()

        // Check search shows
        onView(withId(R.id.ml_menu_filter))
                .perform(click())

        Espresso.pressBack()
        Espresso.pressBack()

        // Check everything is reset
        onView(withId(R.id.ml_menu_filter))
                .check(matches(isDisplayed()))
        onView(withId(R.id.ml_menu_sortby))
                .check(matches(isDisplayed()))
    }

    companion object {
        val DUMMY_PLAYLIST = "test"
    }
}