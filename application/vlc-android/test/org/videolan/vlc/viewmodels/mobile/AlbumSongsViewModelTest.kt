package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import org.junit.Assert.*
import org.junit.Test
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE

class AlbumSongsViewModelTest : BaseTest() {
    private lateinit var albumSongsViewModel: AlbumSongsViewModel
    private lateinit var parent: MediaLibraryItem

    private val dataSource = StubDataSource.getInstance()

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
    }

    private fun setupViewModel(name: String, isArtist: Boolean) {
        if (isArtist)
            parent = MLServiceLocator.getAbstractArtist(dataSource.uuid, name, "", "", "")
        else
            parent = MLServiceLocator.getAbstractGenre(dataSource.uuid, name)
        albumSongsViewModel = AlbumSongsViewModel(context, parent)
    }

    private fun createDummyAudios(count: Int, title: String): List<Long> = (1..count).map {
        dataSource.addMediaWrapper("$title $it", MediaWrapper.TYPE_AUDIO).id
    }

    private fun waitForProvidersData() = albumSongsViewModel.providers.map {
        it.pagedList.test().awaitValue()
    }

    @Test
    fun whenNoTrackExist_checkResultIsEmpty() {
        setupViewModel("test", false)
        waitForProvidersData()

        assertTrue(albumSongsViewModel.isEmpty())
    }

    @Test
    fun whenNoTrackExistForGivenGenre_checkResultIsEmpty() {
        dataSource.setAudioByCount(2, null)
        setupViewModel("xyz", false)
        waitForProvidersData()
        assertTrue(albumSongsViewModel.isEmpty())
    }

    @Test
    fun whenSomeTrackExistForGivenGenre_checkResultIsNotEmpty() {
        dataSource.setAudioByCount(2, null)
        setupViewModel("Rock", false)
        waitForProvidersData()
        assertFalse(albumSongsViewModel.isEmpty())

        // Both tracks and albums are associated with this genre.
        assertFalse(albumSongsViewModel.tracksProvider.isEmpty())
        assertFalse(albumSongsViewModel.albumsProvider.isEmpty())
    }

    @Test
    fun whenNoTrackExistForGivenArtist_checkResultIsEmpty() {
        dataSource.setAudioByCount(2, null)
        setupViewModel("xyz", true)
        waitForProvidersData()
        assertTrue(albumSongsViewModel.isEmpty())
    }

    @Test
    fun whenSomeTrackExistForGivenArtist_checkResultIsNotEmpty() {
        dataSource.setAudioByCount(2, null)
        setupViewModel("Peter Frampton", true)
        waitForProvidersData()
        assertFalse(albumSongsViewModel.isEmpty())

        // Both tracks and albums are associated with this genre.
        assertFalse(albumSongsViewModel.tracksProvider.isEmpty())
        assertFalse(albumSongsViewModel.albumsProvider.isEmpty())
    }

    @Test
    fun whenMoreThanMaxSizeTracks_checkTotalCountIsTotal() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        dataSource.setAudioByCount(count, null)
        setupViewModel("Rock", false)

        assertEquals(count, albumSongsViewModel.tracksProvider.getTotalCount())
    }

    @Test
    fun whenMoreThanMaxSizeTracks_checkLastResultIsNotLoadedYet() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        dataSource.setAudioByCount(count, null)
        setupViewModel("Rock", false)

        waitForProvidersData()

        assertNull(albumSongsViewModel.tracksProvider.pagedList.test().value()[count - 1])
    }

    @Test
    fun whenMoreThanMaxSizeTracks_checkGetAllReturnsAll() {
        val count = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        dataSource.setAudioByCount(count, null)
        setupViewModel("Rock", false)

        waitForProvidersData()

        assertNotNull(albumSongsViewModel.tracksProvider.getAll()[count - 1])
    }

    @Test
    fun whenNoTrackAndFiltered_checkResultIsEmpty() {
        setupViewModel("Artisto", true)
        albumSongsViewModel.filter("xyz")

        waitForProvidersData()

        assertTrue(albumSongsViewModel.isEmpty())
    }

    @Test
    fun whenThereAreTracksWithArtistButFilteredWithNonExistingTrack_checkTrackResultIsEmpty() {
        createDummyAudios(3, "XYZ")
        setupViewModel("Artisto", true)

        albumSongsViewModel.filter("unknown")

        waitForProvidersData()

        assertTrue(albumSongsViewModel.tracksProvider.isEmpty())
    }

    @Test
    fun whenThereAreAlbumsWithArtistButFilteredWithNonExistingAlbum_checkAlbumResultIsEmpty() {
        createDummyAudios(3, "XYZ")
        setupViewModel("CrazyArtists", true)

        albumSongsViewModel.filter("unknown")

        waitForProvidersData()

        assertTrue(albumSongsViewModel.albumsProvider.isEmpty())
    }

    @Test
    fun whenThereAreTracksWithArtistAndFilteredWithExistingTrack_checkTrackResultIsNotEmpty() {
        createDummyAudios(3, "XYZ")
        setupViewModel("Artisto", true)

        albumSongsViewModel.filter("XYZ")

        waitForProvidersData()

        assertFalse(albumSongsViewModel.tracksProvider.isEmpty())
    }

    @Test
    fun whenThereAreAlbumsWithArtistAndFilteredWithExistingAlbum_checkAlbumResultIsNotEmpty() {
        createDummyAudios(3, "XYZ")
        setupViewModel("CrazyArtists", true) // That's the name of Album Artist

        albumSongsViewModel.filter("CD1")

        waitForProvidersData()

        assertFalse(albumSongsViewModel.albumsProvider.isEmpty())
    }

    @Test
    fun whenArtistHasSomeTracksButFilteredResultContainsNone_restoringViewModelResetsFilterAndShowsItemAgain() {
        createDummyAudios(2, "test")
        setupViewModel("Artisto", true)

        albumSongsViewModel.filter("unknown")

        waitForProvidersData()
        assertTrue(albumSongsViewModel.isEmpty())

        albumSongsViewModel.restore()
        waitForProvidersData()
        assertFalse(albumSongsViewModel.isEmpty())
    }

    @Test
    fun whenFilteredAndLaterRestored_isFilteringIsTrueLaterFalse() {
        setupViewModel("Artisto", true)

        assertFalse(albumSongsViewModel.isFiltering())

        albumSongsViewModel.filter("def")

        assertTrue(albumSongsViewModel.isFiltering())

        albumSongsViewModel.restore()

        assertFalse(albumSongsViewModel.isFiltering())
    }

    @Test
    fun whenArtistHas2TracksAndLaterAdded3Tracks_checkResultIsUpdatedWithThemOnRefresh() {
        createDummyAudios(2, "test")
        setupViewModel("Artisto", true)

        waitForProvidersData()

        assertFalse(albumSongsViewModel.isEmpty())
        assertEquals(2, albumSongsViewModel.tracksProvider.pagedList.test().value().size)

        createDummyAudios(3, "test")
        albumSongsViewModel.refresh()

        waitForProvidersData()

        assertFalse(albumSongsViewModel.isEmpty())
        assertEquals(5, albumSongsViewModel.tracksProvider.pagedList.test().value().size)
    }

    @Test
    fun whenThereAreFourArtistTracksWithAlternatelyDifferentTitles_checkTrackHeadersIsSortedByAlbum() {
        createDummyAudios(2, "test")
        createDummyAudios(2, "fake")
        setupViewModel("Artisto", true)

        waitForProvidersData()

        val trackHeaders = albumSongsViewModel.tracksProvider.liveHeaders.test().value()

        // Assertion for track headers
        assertEquals(1, trackHeaders.size())
        assertEquals("XYZ CD1", trackHeaders[0])
    }

    @Test
    fun whenThereAreFourGenreTracksWithAlternatelyDifferentTitles_checkTrackHeadersIsSortedByName() {
        createDummyAudios(2, "test")
        createDummyAudios(2, "fake")
        setupViewModel("Jazz", false)

        waitForProvidersData()

        val trackHeaders = albumSongsViewModel.tracksProvider.liveHeaders.test().value()

        // Assertion for track headers
        assertEquals(2, trackHeaders.size())
        assertEquals("F", trackHeaders[0])
        assertEquals("T", trackHeaders[2])
    }

    @Test
    fun whenThereAreFourArtistTracksWithAlternatelyDifferentTitles_checkAlbumHeadersIsSortedByDate() {
        createDummyAudios(2, "test")
        createDummyAudios(2, "fake")
        setupViewModel("CrazyArtists", true)

        waitForProvidersData()

        val albumHeaders = albumSongsViewModel.albumsProvider.liveHeaders.test().value()

        // Assertion for album headers
        assertEquals(1, albumHeaders.size())
        assertEquals("2018", albumHeaders[0])
    }

    @Test
    fun whenThereAreFourGenreTracksWithAlternatelyDifferentTitles_checkAlbumHeadersIsSortedByName() {
        createDummyAudios(2, "test")
        createDummyAudios(2, "fake")
        setupViewModel("Jazz", false)

        waitForProvidersData()

        val albumHeaders = albumSongsViewModel.albumsProvider.liveHeaders.test().value()

        // Assertion for album headers
        assertEquals(1, albumHeaders.size())
        assertEquals("X", albumHeaders[0])
    }
}