package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.vlc.util.MEDIALIBRARY_PAGE_SIZE

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideosViewModelTest : BaseTest() {
    private lateinit var videosViewModel: VideosViewModel

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
    }

    private fun setupViewModel(folder: AbstractFolder?) {
        videosViewModel = VideosViewModel(context, application, folder)
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

        assertEquals(2, testResult.size)
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

        assertEquals(2, videosViewModel.provider.getTotalCount())
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
}