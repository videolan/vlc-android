package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import org.junit.Assert.*
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE

class VideosViewModelTest : BaseTest() {
    private lateinit var videosViewModel: VideosViewModel

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
    }

    private fun setupViewModel(folder: Folder?) {
        videosViewModel = VideosViewModel(context, VideoGroupingType.NONE, folder, null)
    }

    @Test
    fun whenFolderIsNull_checkResultIsEmpty() {
        setupViewModel(null)

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsNullAndMediaLibraryHasPagedVideos_checkResultContainsThem() {
        val videoCount = 2
        setupViewModel(null)

        StubDataSource.getInstance().setVideoByCount(videoCount, null)

        val testResult = videosViewModel.provider.pagedList.test()
                .awaitValue().value()

        assertEquals(2, testResult.size)
    }

    @Test
    fun whenFolderIsGivenAndItIsEmpty_checkResultIsEmpty() {
        setupViewModel(StubDataSource.getInstance().createFolder("test"))

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsGivenAndItHasVideosAndAudio_checkPagedListContainsOnlyVideos() {
        val videoCount = 2
        val audioCount = 1
        setupViewModel(StubDataSource.getInstance().createFolder("test"))

        StubDataSource.getInstance().setVideoByCount(videoCount, "test")
        StubDataSource.getInstance().setAudioByCount(audioCount, "test")

        val testResult = videosViewModel.provider.pagedList.test()
                .awaitValue().value()

        assertEquals(videoCount, testResult.size)
    }

    @Test
    fun whenFolderIsGivenAndItHasVideosAndAudio_checkTotalCountHasOnlyVideos() {
        val videoCount = 2
        val audioCount = 1
        setupViewModel(StubDataSource.getInstance().createFolder("test"))

        StubDataSource.getInstance().setVideoByCount(videoCount, "test")
        StubDataSource.getInstance().setAudioByCount(audioCount, "test")

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertEquals(videoCount, videosViewModel.provider.getTotalCount())
    }

    @Test
    fun whenFolderIsNullAndVideosAreMoreThanMaxSize_checkLastIsNotLoadedYet() {
        val videoCount = MEDIALIBRARY_PAGE_SIZE * 3 + 1
        setupViewModel(null)
        StubDataSource.getInstance().setVideoByCount(videoCount, "test")

        val testResult = videosViewModel.provider.pagedList.test()
                .awaitValue()
                .value()

        assertEquals(null, testResult[videoCount - 1])
    }

    @Test
    fun whenFolderIsNullAndItHasVideos_checkGetAllReturnsAll() {
        val videoCount = MEDIALIBRARY_PAGE_SIZE + 1
        val audioCount = 200
        setupViewModel(StubDataSource.getInstance().createFolder("test"))

        StubDataSource.getInstance().setVideoByCount(videoCount, "test")
        StubDataSource.getInstance().setAudioByCount(audioCount, "test")

        val testResult = videosViewModel.provider.getAll()

        assertEquals(videoCount, testResult.size)
    }

    @Test
    fun whenFolderIsNullAndItWasEmptyAndAddedNewVideo_checkRefreshUpdatesTheList() {
        setupViewModel(null)

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())

        StubDataSource.getInstance().setVideoByCount(1, null)
        videosViewModel.refresh()

        val testResult = videosViewModel.provider.pagedList.test()
                .awaitValue().value()

        assertFalse(videosViewModel.isEmpty())
        assertEquals(1, testResult.size)
    }

    @Test
    fun whenFolderIsNullAndEmptyAndFiltered_checkResultIsEmpty() {
        setupViewModel(null)
        videosViewModel.filter("xyz")
        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsGivenAndEmptyAndFiltered_checkResultIsEmpty() {
        setupViewModel(StubDataSource.getInstance().createFolder("test"))
        videosViewModel.filter("xyz")
        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsNullAndNotEmptyAndFilteredWithNonExistingVideo_checkResultIsEmpty() {
        setupViewModel(null)
        StubDataSource.getInstance().setVideoByCount(3, null)

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())

        videosViewModel.filter("unknown")
        videosViewModel.refresh()

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsGivenAndNotEmptyAndFilteredWithNonExistingVideo_checkResultIsEmpty() {
        setupViewModel(StubDataSource.getInstance().createFolder("test"))
        StubDataSource.getInstance().setVideoByCount(3, "test")

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())

        videosViewModel.filter("unknown")
        videosViewModel.refresh()

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(videosViewModel.isEmpty())
    }

    @Test
    fun whenFolderIsNullAndNotEmptyAndFilteredWithExistingVideoTitle_checkResultContainsThem() {
        setupViewModel(null)
        StubDataSource.getInstance().setVideoByCount(3, null)

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())

        videosViewModel.filter(StubDataSource.STUBBED_VIDEO_TITLE.substring(1, 6))
        videosViewModel.refresh()

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())
        assertEquals(3, videosViewModel.provider.getTotalCount())
    }

    @Test
    fun whenFolderIsGivenAndNotEmptyAndFilteredWithExistingVideoTitle_checkResultContainsThem() {
        setupViewModel(StubDataSource.getInstance().createFolder("test"))
        StubDataSource.getInstance().setVideoByCount(3, "test")

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())

        videosViewModel.filter(StubDataSource.STUBBED_VIDEO_TITLE.substring(1, 6))
        videosViewModel.refresh()

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(videosViewModel.isEmpty())
        assertEquals(3, videosViewModel.provider.getTotalCount())
    }

    @Test
    fun whenFolderIsGivenAndNotEmptyButFilteredResultContainsNone_restoringViewModelResetsFilterAndShowsItemAgain() {
        setupViewModel(StubDataSource.getInstance().createFolder("test"))
        StubDataSource.getInstance().setVideoByCount(3, "test")

        videosViewModel.filter("unknown")

        videosViewModel.provider.pagedList.test()
                .awaitValue()
        assertTrue(videosViewModel.isEmpty())

        videosViewModel.restore()
        videosViewModel.provider.pagedList.test()
                .awaitValue()
        assertFalse(videosViewModel.isEmpty())
    }

    @Test
    fun whenFilteredAndLaterRestored_isFilteringIsTrueLaterFalse() {
        setupViewModel(null)
        StubDataSource.getInstance().setVideoByCount(3, null)

        assertFalse(videosViewModel.isFiltering())

        videosViewModel.filter(StubDataSource.STUBBED_VIDEO_TITLE.substring(2, 6))

        assertTrue(videosViewModel.isFiltering())

        videosViewModel.restore()

        assertFalse(videosViewModel.isFiltering())
    }

    @Test
    fun whenFolderIsNullAndItHasItems_checkHeaders() {
        setupViewModel(null)
        StubDataSource.getInstance().setVideoByCount(3, null)

        videosViewModel.provider.pagedList.test()
                .awaitValue()

        val headers = videosViewModel.provider.liveHeaders.test()
                .value()

        // Can only test with 1 special header, because of limitation of partially-fixed title in stubbed data
        assertEquals(1, headers.size())
        assertEquals("#", headers[0])
    }
}