package org.videolan.vlc.viewmodels

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.BaseTest
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.PageResponse
import org.videolan.vlc.discourse.PaginationMeta

class DiscourseViewModelTest : BaseTest() {
    @Test
    fun loadsEveryPageAndSortsIgnoringCase() {
        val pages = mutableListOf<Int>()
        val model = model { page ->
            pages += page
            page(listOf(discourse(if (page == 1) "zebra" else "Alpha")), page, 2)
        }

        model.load()

        assertEquals(listOf(1, 2), pages)
        assertEquals(listOf("Alpha", "zebra"), catalogue(model).map { it.title })
    }

    @Test
    fun exposesEmptyCatalogue() {
        val model = model { page(emptyList()) }
        model.load()
        assertTrue(catalogue(model).isEmpty())
    }

    @Test
    fun firstPageFailureShowsError() {
        val model = model { throw IllegalStateException() }
        model.load()
        assertTrue(model.state.value is DiscourseViewModel.State.Error)
    }

    @Test
    fun laterPageFailureDoesNotExposePartialResults() {
        val model = model { number ->
            if (number == 2) throw IllegalStateException()
            page(listOf(discourse("partial")), totalPages = 2)
        }
        model.load()
        assertTrue(model.state.value is DiscourseViewModel.State.Error)
    }

    @Test
    fun retryWorksAndConcurrentLoadsAreIgnored() {
        var calls = 0
        val pending = CompletableDeferred<PageResponse<Discourse>>()
        val model = model {
            calls++
            if (calls == 1) throw IllegalStateException()
            pending.await()
        }
        model.load()
        model.load()
        model.load()
        assertEquals(2, calls)
        pending.complete(page(listOf(discourse("ready"))))
        assertEquals(listOf("ready"), catalogue(model).map { it.title })
    }

    private fun model(loader: suspend (Int) -> PageResponse<Discourse>) =
        DiscourseViewModel(loader, { page(emptyList()) }) { emptyList() }

    private fun catalogue(model: DiscourseViewModel) =
        (model.state.value as DiscourseViewModel.State.Catalogue).discourses

    private fun discourse(title: String) = Discourse(
        id = title,
        title = title,
        thumbnailUrl = null,
        isAudioCleaned = false,
        slug = null,
        createdAt = "",
        updatedAt = ""
    )

    private fun <T> page(items: List<T>, page: Int = 1, totalPages: Int = 1) =
        PageResponse(items, PaginationMeta(page, 16, items.size, totalPages))
}
