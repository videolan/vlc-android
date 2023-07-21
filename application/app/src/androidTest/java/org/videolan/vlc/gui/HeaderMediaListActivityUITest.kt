package org.videolan.vlc.gui

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.ActivityTestRule
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.vlc.BaseUITest
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AudioBrowserItemBinding
import org.videolan.vlc.findFirstPosition
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.sizeOfAtLeast
import org.videolan.vlc.withMediaItem
import org.videolan.vlc.withRecyclerView

class HeaderMediaListActivityUITest: BaseUITest() {
    @Rule
    @JvmField
    val activityTestRule = ActivityTestRule(HeaderMediaListActivity::class.java, true, false)

    lateinit var activity: HeaderMediaListActivity

    override fun beforeTest() {
        // TODO: Hack because of IO Dispatcher used in MediaParsingService channel
        Thread.sleep(3 * 1000)

        val ml = Medialibrary.getInstance()
        val pl = ml.createPlaylist("test", true, false)
        pl.append(ml.getPagedVideos(Medialibrary.SORT_DEFAULT, false, true, false, 5, 0).map { it.id })
        pl.append(ml.getPagedAudio(Medialibrary.SORT_DEFAULT, false, true, false, 5, 0).map { it.id })

        val intent = Intent().apply {
            putExtra(AudioBrowserFragment.TAG_ITEM, pl)
        }
        activityTestRule.launchActivity(intent)
        activity = activityTestRule.activity
    }

    @Test
    fun whenAtTestPlaylist_checkMediaListAndPlayButton() {
        onView(withId(R.id.songs))
                .check(matches(sizeOfAtLeast(1)))

        onView(withId(R.id.fab))
                .check(matches(isDisplayed()))
    }

    @Test
    fun whenAtTestPlaylist_checkDragMediaWorks() {
        val rvMatcher = withRecyclerView(R.id.songs)

        onView(rvMatcher.atPosition(0))
                .check(matches(isDisplayed()))

        val recyclerView = rvMatcher.recyclerView!!
        val adapter = recyclerView.adapter as AudioBrowserAdapter

        val count = recyclerView.adapter!!.itemCount

        val firstItem = (recyclerView.findViewHolderForAdapterPosition(0) as AudioBrowserAdapter.AbstractMediaItemViewHolder<AudioBrowserItemBinding>).binding.item.also { println(it!!.title) }

        val finalCoord = CoordinatesProvider {
            val coords = GeneralLocation.BOTTOM_CENTER.calculateCoordinates(it)
            coords[1] = recyclerView.measuredHeight.toFloat() //point.y.toFloat()
            coords
        }
        onView(rvMatcher.atPositionOnView(0, R.id.item_move))
                .perform(GeneralSwipeAction(Swipe.SLOW, GeneralLocation.TOP_CENTER, finalCoord, Press.FINGER))

        // To reflect the update in adapter's dataset
        Thread.sleep(1000)

        val newPos = findFirstPosition(adapter, withMediaItem(firstItem))
        assertThat(newPos, equalTo(count - 1))
    }
}
