package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import org.junit.Assert.*
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE

class PlaylistViewModelTest : BaseTest() {
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var parent: Playlist

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
    }

    private fun createDummyAudios(count: Int, title: String): List<Long> = (0 until count).map {
        StubDataSource.getInstance().addMediaWrapper("$title $it", MediaWrapper.TYPE_AUDIO).id
    }

    private fun waitForProvidersData() = playlistViewModel.providers.map {
        it.pagedList.test().awaitValue()
    }

    private fun setupViewModel(playlist: String, mediaIds: List<Long>? = null) {
        parent = medialibrary.createPlaylist(playlist).apply { if (mediaIds != null) append(mediaIds) }
        playlistViewModel = PlaylistViewModel(context, parent)
    }

    @Test
    fun whenParentHasNoTracks_checkResultIsEmpty() {
        setupViewModel(PLAYLIST_PREFIX)

        waitForProvidersData()

        assertTrue(playlistViewModel.isEmpty())
    }

    @Test
    fun whenParentHas2Tracks_checkResultContainsThem() {
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(2, "test"))

        waitForProvidersData()

        val testResult = playlistViewModel.tracksProvider.pagedList.test()
                .value()

        assertEquals(2, testResult.size)
        assertEquals("test 0", testResult[0]!!.title)
        assertEquals("test 1", testResult[1]!!.title)
    }

    @Test
    fun whenParentHasNoTracks_checkTotalCountIsZero() {
        setupViewModel(PLAYLIST_PREFIX)

        assertEquals(0, playlistViewModel.tracksProvider.getTotalCount())
    }

    @Test
    fun whenParentHasMoreThanMaxSizeTracks_checkTotalCountIsTotal() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(count, "test"))

        assertEquals(count, playlistViewModel.tracksProvider.getTotalCount())
    }

    @Test
    fun whenParentHasMoreThanMaxSizeTrack_checkLastIsNotLoadedYet() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(count, "test"))

        waitForProvidersData()
        val testResult = playlistViewModel.tracksProvider.pagedList.test().value()

        assertNull(testResult[count - 1])
    }

    @Test
    fun whenParentHasMoreThanMaxSizeTracks_checkGetAllReturnsAll() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(count, "test"))

        assertNotNull(playlistViewModel.tracksProvider.getAll()[count - 1])
    }

    @Test
    fun whenParentHasSomeTracksOnly_checkResultContainsOnlyThese() {
        val realCount = 5
        val extraCount = 3
        val realIds = createDummyAudios(realCount, "real")
        // extraIds are those audio IDs which aren't tracks of the given playlist
        val extraIds = createDummyAudios(extraCount, "extra")

        setupViewModel(PLAYLIST_PREFIX, realIds)

        waitForProvidersData()
        val testResult = playlistViewModel.tracksProvider.pagedList.test().value()

        assertEquals(realCount, testResult.size)
        assertEquals("real 0", testResult[0]!!.title)
    }

    @Test
    fun whenParentHasNoTrackAndFiltered_checkResultIsEmpty() {
        setupViewModel(PLAYLIST_PREFIX)
        playlistViewModel.filter("xyz")

        waitForProvidersData()

        assertTrue(playlistViewModel.isEmpty())
    }

    @Test
    fun whenParentHasTracksButFilteredWithNonExistingTrack_checkResultIsEmpty() {
        val realCount = 5
        val extraCount = 3
        val realIds = createDummyAudios(realCount, "real")
        // extraIds are those audio IDs which aren't tracks of the given playlist
        val extraIds = createDummyAudios(extraCount, "extra")

        setupViewModel(PLAYLIST_PREFIX, realIds)
        playlistViewModel.filter("extra")

        waitForProvidersData()

        assertTrue(playlistViewModel.isEmpty())
    }

    @Test
    fun whenParentHasTracksButFilteredWithExistingTrack_checkResultContainsFilteredOnes() {
        val realCount = 5
        val extraCount = 3
        val realIds = createDummyAudios(realCount, "real")
        // extraIds are those audio IDs which aren't tracks of the given playlist
        val extraIds = createDummyAudios(extraCount, "extra")

        setupViewModel(PLAYLIST_PREFIX, realIds)
        playlistViewModel.filter("real")

        waitForProvidersData()

        assertFalse(playlistViewModel.isEmpty())
        assertEquals(realCount, playlistViewModel.tracksProvider.pagedList.test().value().size)
    }

    @Test
    fun whenParentIsEmptyAndLaterAddedTracks_checkResultIsUpdatedWithThemOnRefresh() {
        setupViewModel(PLAYLIST_PREFIX)

        waitForProvidersData()

        assertTrue(playlistViewModel.isEmpty())

        parent.append(createDummyAudios(3, "test"))
        playlistViewModel.refresh()

        waitForProvidersData()

        assertFalse(playlistViewModel.isEmpty())
        assertEquals(3, playlistViewModel.tracksProvider.pagedList.test().value().size)
    }

    @Test
    fun whenFilteredAndLaterRestored_isFilteringIsTrueLaterFalse() {
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(3, "test"))

        assertFalse(playlistViewModel.isFiltering())

        playlistViewModel.filter("def")

        assertTrue(playlistViewModel.isFiltering())

        playlistViewModel.restore()

        assertFalse(playlistViewModel.isFiltering())
    }

    @Test
    fun whenPlaylistHasSomeTracksButFilteredResultContainsNone_restoringViewModelResetsFilterAndShowsItemAgain() {
        setupViewModel(PLAYLIST_PREFIX, createDummyAudios(2, "test"))

        playlistViewModel.filter("unknown")

        waitForProvidersData()
        assertTrue(playlistViewModel.isEmpty())

        playlistViewModel.restore()
        waitForProvidersData()
        assertFalse(playlistViewModel.isEmpty())
    }

    @Test
    fun whenPlaylistHasFourTracksWithAlternatelyDifferentTitles_checkHeadersContains2Letters() {
        setupViewModel(PLAYLIST_PREFIX)

        parent.append(createDummyAudios(2, "test"))
        parent.append(createDummyAudios(2, "fake"))

        playlistViewModel.refresh()
        waitForProvidersData()

        val headers = playlistViewModel.tracksProvider.liveHeaders.test()
                .value()

        assertEquals(2, headers.size())
        assertEquals("T", headers[0])
        assertEquals("F", headers[2])
    }

    companion object {
        private val PLAYLIST_PREFIX = "playlist_"
    }
}