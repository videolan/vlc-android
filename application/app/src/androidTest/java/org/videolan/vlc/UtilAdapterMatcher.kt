package org.videolan.vlc

import androidx.annotation.IdRes
import androidx.paging.PagedListAdapter
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.DiffUtilAdapter
import kotlin.math.min


abstract class DiffAdapterMatcher<D> : TypeSafeMatcher<D>()

fun withMediaType(mediaType: Int): DiffAdapterMatcher<MediaLibraryItem> {
    return object : DiffAdapterMatcher<MediaLibraryItem>() {
        override fun describeTo(description: Description) {
            description.appendText("with media type: $mediaType")
        }

        override fun matchesSafely(item: MediaLibraryItem?): Boolean {
            return (item as? MediaWrapper)?.type == mediaType
        }
    }
}

fun withMediaItem(mediaItem: MediaLibraryItem?): DiffAdapterMatcher<MediaLibraryItem> {
    return object : DiffAdapterMatcher<MediaLibraryItem>() {
        override fun describeTo(description: Description) {
            description.appendText("with media item: ${mediaItem?.title}")
        }

        override fun matchesSafely(item: MediaLibraryItem?): Boolean {
            return mediaItem?.equals(item) ?: true
        }
    }
}

fun <D, VH : RecyclerView.ViewHolder> findFirstPosition(adapter: DiffUtilAdapter<D, VH>, vararg matchers: DiffAdapterMatcher<D>): Int {
    val iter = adapter.dataset.iterator().withIndex()
    while (iter.hasNext()) {
        val index = iter.next()
        if (matchers.all { it.matches(index.value) })
            return index.index
    }
    return -1
}

fun <D : Any, VH : RecyclerView.ViewHolder> findFirstPosition(adapter: PagedListAdapter<D, VH>, vararg matchers: DiffAdapterMatcher<D>): Int  {
    val iter = adapter.currentList!!.iterator().withIndex()
    while (iter.hasNext()) {
        val index = iter.next()
        if (matchers.all { it.matches(index.value) })
            return index.index
    }
    return -1
}

fun findFirstPreferencePosition(@IdRes recyclerViewId: Int, vararg matchers: Matcher<Preference>): Pair<Int, Int> {
    val rvMatcher = withRecyclerView(recyclerViewId)
    onView(rvMatcher.atPosition(0)).check(matches(isDisplayed()))

    val adapter = rvMatcher.recyclerView?.adapter as PreferenceGroupAdapter
    val count = adapter.itemCount
    for (i in 0..count) {
        if (matchers.all { it.matches(adapter.getItem(i)) })    return i to count
    }
    return -1 to count
}

fun onPreferenceRow(@IdRes recyclerViewId: Int, vararg matchers: Matcher<Preference>): ViewInteraction? = findFirstPreferencePosition(recyclerViewId, *matchers).let { (i, count) ->
    if (i != -1) {
        // FIXME: Fails to scroll to the bottom of recycler view
        onView(withId(recyclerViewId))
                .perform(RecyclerViewActions.scrollToPosition<PreferenceViewHolder>(min(i + 1, count - 1)))
        onView(withRecyclerView(recyclerViewId).atPosition(i))
    } else null
}
