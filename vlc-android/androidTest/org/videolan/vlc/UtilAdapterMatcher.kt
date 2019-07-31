package org.videolan.vlc

import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.DiffUtilAdapter


abstract class DiffAdapterMatcher<D> : TypeSafeMatcher<D>()

fun withMediaType(mediaType: Int): DiffAdapterMatcher<MediaLibraryItem> {
    return object : DiffAdapterMatcher<MediaLibraryItem>() {
        override fun describeTo(description: Description) {
            description.appendText("with media type: $mediaType")
        }

        override fun matchesSafely(item: MediaLibraryItem?): Boolean {
            return (item as? AbstractMediaWrapper)?.type == mediaType
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

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun <D, VH : RecyclerView.ViewHolder> findFirstPosition(adapter: DiffUtilAdapter<D, VH>?, vararg matchers: DiffAdapterMatcher<D>): Int = adapter?.let { it ->
    val iter = it.dataset.iterator().withIndex()
    while (iter.hasNext()) {
        val index = iter.next()
        if (matchers.all { it.matches(index.value) })
            return index.index
    }
    return -1
} ?: -1

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun <D, VH : RecyclerView.ViewHolder> findFirstPosition(adapter: PagedListAdapter<D, VH>?, vararg matchers: DiffAdapterMatcher<D>): Int = adapter?.let {
    val iter = it.currentList!!.iterator().withIndex()
    while (iter.hasNext()) {
        val index = iter.next()
        if (matchers.all { it.matches(index.value) })
            return index.index
    }
    return -1
} ?: -1
