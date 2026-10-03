package org.videolan.vlc.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.BaseTest
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.PageResponse
import org.videolan.vlc.discourse.PaginationMeta

class DiscourseViewModelTest : BaseTest() {
    @Test
    fun loadsPagesIncrementally() {
        val pages = mutableListOf<Pair<Int, Boolean>>()
        val model = model { number, force ->
            pages += number to force
            page(listOf(discourse("page$number")), number, 2)
        }
        model.load()
        assertEquals(listOf(1 to false), pages)
        model.loadMore()
        assertEquals(listOf(1 to false, 2 to false), pages)
        assertEquals(listOf("page1", "page2"), catalogue(model).map { it.title })
    }

    @Test
    fun refreshForcesEveryPageInTheNewSession() {
        val pages = mutableListOf<Pair<Int, Boolean>>()
        val model = model { number, force ->
            pages += number to force
            page(listOf(discourse("page$number")), number, 2)
        }
        model.load()
        model.refresh()
        model.loadMore()
        assertEquals(listOf(1 to false, 1 to true, 2 to true), pages)
    }

    @Test
    fun normalSelectionUsesCacheAndRetryForcesTracks() {
        val loads = mutableListOf<Boolean>()
        val model = DiscourseViewModel({ _, _, _, _ -> page(emptyList()) }) { _, force ->
            loads += force
            if (loads.size == 1) throw IllegalStateException()
            emptyList()
        }
        model.select(discourse("detail"))
        assertTrue((model.state.value as DiscourseViewModel.State.Detail).error)
        model.retryDetail()
        assertEquals(listOf(false, true), loads)
    }

    @Test
    fun firstPageFailureShowsError() {
        val model = model { _, _ -> throw IllegalStateException() }
        model.load()
        assertTrue(model.state.value is DiscourseViewModel.State.Error)
    }

    @Test
    fun laterPageFailureKeepsLoadedItems() {
        val model = model { number, _ ->
            if (number == 2) throw IllegalStateException()
            page(listOf(discourse("first")), totalPages = 2)
        }
        model.load()
        model.loadMore()
        assertEquals(listOf("first"), catalogue(model).map { it.title })
    }

    @Test
    fun filtersAreSentToEveryPageAndResetPagination() {
        val requests = mutableListOf<List<Any?>>()
        val model = DiscourseViewModel({ number, _, language, sort ->
            requests += listOf(number, language, sort)
            page(listOf(discourse("page$number")), number, 2)
        }) { _, _ -> emptyList() }

        model.load()
        model.loadMore()
        model.setFilters(DiscourseViewModel.LanguageFilter.HINDI, DiscourseViewModel.SortFilter.MOST_LIKED)
        model.loadMore()

        assertEquals(
            listOf(
                listOf(1, null, null),
                listOf(2, null, null),
                listOf(1, "hindi", "most_liked"),
                listOf(2, "hindi", "most_liked")
            ),
            requests
        )
    }

    @Test
    fun restoredFiltersAndChangesArePersisted() {
        val saved = mutableListOf<Pair<String?, String?>>()
        val model = DiscourseViewModel(
            { _, _, _, _ -> page(emptyList()) },
            initialLanguage = "english",
            initialSort = "most_liked",
            saveFilters = { language, sort -> saved += language to sort }
        ) { _, _ -> emptyList() }

        assertEquals(DiscourseViewModel.LanguageFilter.ENGLISH, model.languageFilter)
        assertEquals(DiscourseViewModel.SortFilter.MOST_LIKED, model.sortFilter)
        model.setFilters(DiscourseViewModel.LanguageFilter.ALL, DiscourseViewModel.SortFilter.DEFAULT)

        assertEquals(listOf(null to null), saved)
    }

    @Test
    fun recordsRecentlyPlayedDiscourseThroughCallback() {
        val saved = mutableListOf<Discourse>()
        val selected = discourse("selected")
        val model = DiscourseViewModel(
            { _, _, _, _ -> page(emptyList()) },
            saveRecentlyPlayed = { saved += it }
        ) { _, _ -> emptyList() }

        model.recordRecentlyPlayed(selected)

        assertEquals(listOf(selected), saved)
    }

    private fun model(loader: suspend (Int, Boolean) -> PageResponse<Discourse>) =
        DiscourseViewModel({ page, force, _, _ -> loader(page, force) }) { _, _ -> emptyList() }

    private fun catalogue(model: DiscourseViewModel) =
        (model.state.value as DiscourseViewModel.State.Catalogue).discourses

    private fun discourse(title: String) = Discourse(title, title, null, false, "hindi", null, "", "")

    private fun page(items: List<Discourse>, page: Int = 1, totalPages: Int = 1) =
        PageResponse(items, PaginationMeta(page, 16, items.size, totalPages))
}
