package org.videolan.vlc.gui

import android.content.Intent
import android.widget.EditText
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.contrib.DrawerActions.*
import androidx.test.espresso.matcher.RootMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import com.google.android.material.internal.NavigationMenuItemView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.BaseUITest
import org.videolan.vlc.R
import org.videolan.vlc.*
import org.videolan.vlc.util.EXTRA_TARGET

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlaylistFragmentUITest: BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(MainActivity::class.java, true, false)

    lateinit var activity: MainActivity

    override fun beforeTest() {
        val intent = Intent().apply {
            putExtra(EXTRA_TARGET, R.id.nav_playlists)
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
    }

    private fun createDummyPlaylist() {
        val ml = AbstractMedialibrary.getInstance()
        val pl = ml.createPlaylist("test")
        pl.append(ml.getPagedVideos(AbstractMedialibrary.SORT_DEFAULT, false, 5, 0).map { it.id })
        pl.append(ml.getPagedAudio(AbstractMedialibrary.SORT_DEFAULT, false, 5, 0).map { it.id })
    }

    @Test
    fun whenAtRoot_checkAppbar() {
        onView(withId(R.id.ml_menu_filter))
                .check(matches(isDisplayed()))
        onView(withId(R.id.ml_menu_sortby))
                .check(matches(isDisplayed()))

        openActionBarOverflowOrOptionsMenu(context)

        onView(withText(R.string.refresh))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenNoMedia_checkListEmpty() {
        onView(withId(R.id.audio_list))
                .check(matches(withCount(equalTo(0))))
    }

    @Test
    fun whenPlaylistAddedFromDirectoriesView_checkPlaylistUpdated() {
        // Navigate to directories view
        onView(withId(R.id.root_container))
                .perform(open())

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

        // Because playlist is saved with IO dispatcher.
        // TODO: Once tests_vm is merged, I'll update the WorkersKt to use CoroutineContextProvider and remove this hack.
        Thread.sleep(2000)

        // Navigate back to playlists view
        onView(withId(R.id.root_container))
                .perform(open())

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
        Thread.sleep(1500)

        onView(withId(R.id.audio_list))
                .check(matches(withCount(equalTo(1))))

        val rvMatcher = withRecyclerView(R.id.audio_list)
        onView(rvMatcher.atPositionOnView(0, R.id.title))
                .check(matches(withText("test")))
    }
}