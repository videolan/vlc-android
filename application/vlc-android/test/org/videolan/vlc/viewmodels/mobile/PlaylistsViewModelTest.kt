package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import org.junit.Assert.*
import org.junit.Test
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE

class PlaylistsViewModelTest : BaseTest() {
    private lateinit var playlistsViewModel: PlaylistsViewModel
    private val mediaLibrary = MLServiceLocator.getAbstractMedialibrary()

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
        playlistsViewModel = PlaylistsViewModel(context)
    }

    private fun createDummyPlaylists(count: Int) {
        (0 until count).map { mediaLibrary.createPlaylist("test$it") }
    }

    @Test
    fun whenNoPlaylist_checkResultIsEmpty() {
        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(playlistsViewModel.isEmpty())
    }

    @Test
    fun whenThereAre2Playlists_checkResultContainsThem() {
        createDummyPlaylists(2)

        val testResult = playlistsViewModel.provider.pagedList.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
        assertEquals("test0", testResult[0]!!.title)
        assertEquals("test1", testResult[1]!!.title)
    }

    @Test
    fun whenNoPlaylists_checkTotalCountIsZero() {
        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertEquals(0, playlistsViewModel.provider.getTotalCount())
    }

    @Test
    fun whenThereAre2Playlists_checkTotalCountIsTwo() {
        createDummyPlaylists(2)

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertEquals(2, playlistsViewModel.provider.getTotalCount())
    }

    @Test
    fun whenPlaylistsAreMoreThanMaxSize_checkLastIsNotLoadedYet() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        createDummyPlaylists(count)

        val testResult = playlistsViewModel.provider.pagedList.test()
                .awaitValue()
                .value()

        assertNull(testResult[count - 1])
    }

    @Test
    fun whenPlayListsAreMoreThanMaxSize_checkGetAllReturnsAll() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        createDummyPlaylists(count)

        val testResult = playlistsViewModel.provider.getAll()

        assertNotNull(testResult[count - 1])
    }

    @Test
    fun whenPlayListsAreMoreThanMaxSize_checkTotalCountReturnsTotal() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        createDummyPlaylists(count)

        assertEquals(count, playlistsViewModel.provider.getTotalCount())
    }

    @Test
    fun whenNoPlaylistsAndLaterAdded2Playlists_checkRefreshUpdatesTheList() {
        playlistsViewModel.provider.pagedList.test()
                .awaitValue()
        assertTrue(playlistsViewModel.isEmpty())
        assertEquals(0, playlistsViewModel.provider.getTotalCount())

        createDummyPlaylists(2)

        playlistsViewModel.refresh()

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(playlistsViewModel.isEmpty())
        assertEquals(2, playlistsViewModel.provider.getTotalCount())
    }

    @Test
    fun whenNoPlaylistsAndFiltered_checkResultIsEmpty() {
        playlistsViewModel.filter("unknown")
        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(playlistsViewModel.isEmpty())
    }

    @Test
    fun whenThereAre2PlaylistsAndFilteredWithNonExistingPlaylist_checkResultIsEmpty() {
        createDummyPlaylists(2)
        playlistsViewModel.filter("unknown")

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(playlistsViewModel.isEmpty())
    }

    @Test
    fun whenThereAre2PlaylistsAndFilteredWithExistingPlaylistTitle_checkResultContainsThem() {
        createDummyPlaylists(2)
        playlistsViewModel.filter("test")

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(playlistsViewModel.isEmpty())
        assertEquals(2, playlistsViewModel.provider.getTotalCount())
    }

    @Test
    fun whenFilteredAndLaterRestored_isFilteringIsTrueLaterFalse() {
        createDummyPlaylists(2)

        assertFalse(playlistsViewModel.isFiltering())

        playlistsViewModel.filter("abc")

        assertTrue(playlistsViewModel.isFiltering())

        playlistsViewModel.restore()

        assertFalse(playlistsViewModel.isFiltering())
    }

    @Test
    fun whenThereAreSomePlaylistsButFilteredResultContainsNone_restoringViewModelResetsFilterAndShowsItemAgain() {
        createDummyPlaylists(2)

        playlistsViewModel.filter("unknown")

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()
        assertTrue(playlistsViewModel.isEmpty())

        playlistsViewModel.restore()
        playlistsViewModel.provider.pagedList.test()
                .awaitValue()
        assertFalse(playlistsViewModel.isEmpty())
    }

    @Test
    fun whenThereAre2PlaylistsStartingWithT_checkHeaders() {
        createDummyPlaylists(2)

        playlistsViewModel.provider.pagedList.test()
                .awaitValue()

        val headers = playlistsViewModel.provider.liveHeaders.test()
                .value()

        assertEquals(1, headers.size())
        assertEquals("T", headers[0])
    }
}