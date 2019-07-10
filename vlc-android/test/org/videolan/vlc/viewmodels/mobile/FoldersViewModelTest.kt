package org.videolan.vlc.viewmodels.mobile

import com.jraska.livedata.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.vlc.util.MEDIALIBRARY_PAGE_SIZE

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FoldersViewModelTest: BaseTest() {
    private lateinit var foldersViewModel: FoldersViewModel

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
        setupViewModel()
    }

    internal fun setupViewModel() {
        foldersViewModel = FoldersViewModel(context, application, AbstractFolder.TYPE_FOLDER_VIDEO)
    }

    @Test
    fun whenNoVideoFolder_checkCountIsZero() {
        assertEquals(0, foldersViewModel.provider.getTotalCount())
    }

    @Test
    fun whenNoVideoFolder_checkGetAllReturnsEmpty() {
        assertEquals(emptyArray(), foldersViewModel.provider.getAll())
    }

    @Test
    fun whenThereAre2FoldersWithVideos_checkCountIs2() {
        StubDataSource.getInstance().run {
            createFolder("test1")
            setVideoByCount(2, "test1")
            createFolder("test2")
            setVideoByCount(3, "test2")
        }

        assertEquals(2, foldersViewModel.provider.getTotalCount())
    }

    @Test
    fun whenThereAre2FoldersWithVideos_checkGetAllReturnsThem() {
        StubDataSource.getInstance().run {
            createFolder("test1")
            setVideoByCount(2, "test1")
            createFolder("test2")
            setVideoByCount(3, "test2")
        }

        val testResult = foldersViewModel.provider.getAll()

        assertEquals(2, testResult.size)
        assertEquals("test1", testResult[0].title)
        assertEquals("test2", testResult[1].title)
    }

    @Test
    fun whenThereAre2FoldersWithVideos_checkGetPageReturnsThem() {
        StubDataSource.getInstance().run {
            createFolder("test1")
            setVideoByCount(2, "test1")
            createFolder("test2")
            setVideoByCount(3, "test2")
        }

        val testResult = foldersViewModel.provider.pagedList.test()
                .awaitValue().value()

        assertEquals(2, testResult.size)
    }

    @Test
    fun whenNoVideoFolder_checkIsEmptyReturnsTrue() {
        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(foldersViewModel.isEmpty())
    }

    @Test
    fun whenThereAre2FoldersWithVideos_checkIsEmptyReturnsFalse() {
        // FIXME: java.lang.NoClassDefFoundError: org/videolan/vlc/util/KextensionsKt (wrong name: org/videolan/vlc/util/KExtensionsKt)
        StubDataSource.getInstance().run {
            createFolder("test1")
            setVideoByCount(2, "test1")
            createFolder("test2")
            setVideoByCount(3, "test2")
        }

        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(foldersViewModel.isEmpty())
    }

    @Test
    fun whenNoVideoFolderAndLaterAddedNewVideo_checkRefreshUpdatesTheList() {
        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(foldersViewModel.isEmpty())

        StubDataSource.getInstance().setVideoByCount(1, "test1")
        foldersViewModel.refresh()

        val testResult = foldersViewModel.provider.pagedList.test()
                .awaitValue().value()

        assertFalse(foldersViewModel.isEmpty())
        assertEquals(1, testResult.size)
    }

    @Test
    fun whenFilteredAndLaterRestored_isFilteringIsTrueLaterFalse() {
        StubDataSource.getInstance().setVideoByCount(3, "test1")

        assertFalse(foldersViewModel.isFiltering())

        foldersViewModel.filter("test")

        assertTrue(foldersViewModel.isFiltering())

        foldersViewModel.restore()

        assertFalse(foldersViewModel.isFiltering())
    }

    @Test
    fun whenNoVideoFolderAndFiltered_checkResultIsEmpty() {
        foldersViewModel.filter("xyz")
        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(foldersViewModel.isEmpty())
    }

    @Test
    fun whenNoVideoFolderAndFilteredWithNonExistingFolder_checkResultIsEmpty() {
        StubDataSource.getInstance().setVideoByCount(3, "test1")

        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(foldersViewModel.isEmpty())

        foldersViewModel.filter("unknown")
        foldersViewModel.refresh()

        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertTrue(foldersViewModel.isEmpty())
    }

    @Test
    fun whenNoVideoFolderAndFilteredWithExistingFolder_checkResultIsNotEmpty() {
        StubDataSource.getInstance().setVideoByCount(3, "test1")

        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(foldersViewModel.isEmpty())

        foldersViewModel.filter("test")
        foldersViewModel.refresh()

        foldersViewModel.provider.pagedList.test()
                .awaitValue()

        assertFalse(foldersViewModel.isEmpty())
    }

    @Test
    fun whenThereAreVideoFoldersButFilteredResultContainsNone_restoringViewModelResetsFilterAndShowsItemAgain() {
        StubDataSource.getInstance().setVideoByCount(3, "test")

        foldersViewModel.filter("unknown")

        foldersViewModel.provider.pagedList.test()
                .awaitValue()
        assertTrue(foldersViewModel.isEmpty())

        foldersViewModel.restore()
        foldersViewModel.provider.pagedList.test()
                .awaitValue()
        assertFalse(foldersViewModel.isEmpty())
    }
}