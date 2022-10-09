package org.videolan.vlc.viewmodels.browser

import android.os.Handler
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.jraska.livedata.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.stubs.StubMedia
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.tools.CoroutineContextProvider
import org.videolan.vlc.BaseTest
import org.videolan.vlc.database.BrowserFavDao
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.TestCoroutineContextProvider
import org.videolan.vlc.util.applyMock
import java.io.File


class FileBrowserModelTest : BaseTest() {
    // Preferences choose directories to add in medialibrary scan.

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockedLibVlc: LibVLC = mockk(relaxed = true)
    private lateinit var mediaBrowser: MediaBrowser
    private val mockedFavoritesDao: BrowserFavDao = mockk(relaxed = true)
    private val mockedFavoritesRepo: BrowserFavRepository = spyk(BrowserFavRepository(mockedFavoritesDao))

    private lateinit var browserModel: BrowserModel
    private lateinit var browserProvider: BrowserProvider

    private val countVideos = 2
    private val countDirs = 4

    init {
        BrowserFavRepository.applyMock(mockedFavoritesRepo)

        // BrowserHandler mocked.
        val handler: Handler = mockk()

        BrowserProvider.overrideCreator = false
        BrowserProvider.registerCreator {
            this@FileBrowserModelTest.mediaBrowser = spyk(MediaBrowser(mockedLibVlc, null, handler))
            mediaBrowser
        }
        BrowserProvider.registerCreator(clazz = CoroutineContextProvider::class.java) { TestCoroutineContextProvider() }
    }

    override fun beforeTest() {
        super.beforeTest()
        setupTestFiles()
    }

    private fun initBrowserModel(url: String?, showHiddenFiles: Boolean, showDummyCategory: Boolean = false) {
        browserModel = BrowserModel(application, url, TYPE_FILE, showHiddenFiles, showDummyCategory, TestCoroutineContextProvider())
        browserProvider = browserModel.provider
        mediaBrowser = BrowserProvider.get(browserProvider)
    }

    private fun setupTestFiles() {
        (0 until countDirs).map { temporaryFolder.newFile("dir$it") }
        (0 until countVideos).map { temporaryFolder.newFile("video$it.mp4") }
    }

    private fun addFileToProvider(i: Int, file: File) {
        val t = StubMedia(mockedLibVlc, "file://${file.path}").apply { if (!file.name.endsWith(".mp4")) type = IMedia.Type.Directory }
        browserProvider.addMedia(StubMediaWrapper(t))
    }

    private fun fillFilesInDataset(file: File) {
        file.listFiles().sorted().mapIndexed(this::addFileToProvider)
    }

    private fun getFakeBrowserFav(index: Int): org.videolan.vlc.mediadb.models.BrowserFav {
        val t = temporaryFolder.newFile("fake_media$index")
        return org.videolan.vlc.mediadb.models.BrowserFav(t.path.toUri(), 0, "vid_$index", null)
    }

    @Test
    fun whenAtRootAndInternalStorageIsEmpty_checkShowsFolderIsEmpty() {
        initBrowserModel(null, showHiddenFiles = false)

        val internalStorage = browserModel.dataset.test()
                .awaitValue()
                .value()[0]

        // TODO Has to wait for browserChannel queue
        Thread.sleep(1000)

        assertTrue(browserModel.isFolderEmpty(internalStorage as MediaWrapper))
    }

    @Test
    fun whenAtRootAndInternalStorageHasDirectories_checkShowsFolderIsNotEmpty() {
        initBrowserModel(null, showHiddenFiles = false)

        val internalStorage = browserModel.dataset.test()
                .awaitValue()
                .value()[0]

        Thread.sleep(1000)
        // TODO Hack because parseSubDirectories is called twice for some reason.
        fillFilesInDataset(temporaryFolder.root)
        Thread.sleep(1000)

        assertFalse(browserModel.isFolderEmpty(internalStorage as MediaWrapper))
    }

    @Test
    fun whenAtRootAndSavedList_checkPrefetchListIsFilled() {
        initBrowserModel(null, showHiddenFiles = false)

        val internalStorage = browserModel.dataset.test()
                .awaitValue()
                .value()[0]

        Thread.sleep(1000)
        fillFilesInDataset(temporaryFolder.root)
        Thread.sleep(1000)

        browserModel.saveList(internalStorage as MediaWrapper)

        initBrowserModel(internalStorage.uri.toString(), false)

        val testResult = browserModel.dataset.test().value()

        assertEquals(countDirs + countVideos, testResult.size)
    }

    @Test
    fun whenAtRootAndHasLocalFavorite_checkDataSetContainsIt() {
        val liveFavorites: MutableLiveData<List<org.videolan.vlc.mediadb.models.BrowserFav>> = MutableLiveData()
        every { mockedFavoritesRepo.localFavorites } returns liveFavorites

        initBrowserModel(null, showHiddenFiles = false)

        val noFav = browserModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(1, noFav.size)

        liveFavorites.value = listOf(getFakeBrowserFav(0))

        val hasFav = browserModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(3, hasFav.size)
    }
}