package org.videolan.vlc.viewmodels.browser

import android.os.Environment
import android.os.Handler
import com.jraska.livedata.test
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.stubs.StubMedia
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.vlc.BaseTest
import org.videolan.vlc.R
import org.videolan.vlc.database.CustomDirectoryDao
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.tools.CoroutineContextProvider
import org.videolan.vlc.util.TestCoroutineContextProvider
import org.videolan.vlc.util.applyMock
import java.io.File

class StorageModelTest : BaseTest() {
    // Preferences choose directories to add in medialibrary scan.

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockedLibVlc: LibVLC = mockk(relaxed = true)
    private val mockedDirectoryDao: CustomDirectoryDao = mockk()
    private val mockedDirectoryRepo: DirectoryRepository = spyk(DirectoryRepository(mockedDirectoryDao))
    private lateinit var mediaBrowser: MediaBrowser

    private lateinit var browserModel: BrowserModel
    private lateinit var browserProvider: BrowserProvider

    private var showHiddenFiles: Boolean = false

    private val countVideos = 2
    private val countDirs = 4
    private val countHiddenDirs = 2

    init {
        DirectoryRepository.applyMock(mockedDirectoryRepo)

        // BrowserHandler mocked.
        val handler: Handler = mockk()

        BrowserProvider.overrideCreator = false
        BrowserProvider.registerCreator {
            mediaBrowser = spyk(MediaBrowser(mockedLibVlc, null, handler))
            mediaBrowser
        }
        BrowserProvider.registerCreator(clazz = CoroutineContextProvider::class.java) { TestCoroutineContextProvider() }
    }

    override fun beforeTest() {
        super.beforeTest()
        setupTestFiles()
    }

    /**
     * Setups the browser model.
     *
     * @param [showHiddenFiles] - whether to show hidden directories too when browsing.
     * @param [url] - URL to browse. null for root.
     *
     */
    private fun initBrowserModel(showHiddenFiles: Boolean, url: String?) {
        this.showHiddenFiles = showHiddenFiles
        browserModel = BrowserModel(application, url, TYPE_STORAGE, showHiddenFiles, false, TestCoroutineContextProvider())
        browserProvider = browserModel.provider
    }

    private fun setupTestFiles() {
        (0 until countDirs).map { temporaryFolder.newFile("dir$it") }
        (0 until countHiddenDirs).map { temporaryFolder.newFile(".hiddenDir$it") }
        (0 until countVideos).map { temporaryFolder.newFile("video$it.mp4") }
    }

    private fun addFileToProvider(i: Int, file: File) {
        if (file.name.startsWith(".") && !showHiddenFiles)
            return
        val t = StubMedia(mockedLibVlc, "file://${file.path}").apply { if (!file.name.endsWith(".mp4")) type = IMedia.Type.Directory }
        browserProvider.addMedia(StubMediaWrapper(t))
    }

    private fun fillFilesInDataset(file: File) {
        file.listFiles().sorted().mapIndexed(this::addFileToProvider)
    }

    @Test
    fun whenAtRootAndNoCustomDirectory_checkOnlyInternalStorageShows() {
        initBrowserModel(false, null)

        val testResult = browserModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(1, testResult.size)
        assertEquals(context.getString(R.string.internal_memory), testResult[0].title)
    }

    @Test
    fun whenAtRootAndTwoCustomDirectoriesWithOneChildOfInternalStorage_checkTwoResultsAreObtained() {
        val customDir = org.videolan.vlc.mediadb.models.CustomDirectory(temporaryFolder.newFile("custom1").path)
        val newDirInsideInternalStorage = File("${Environment.getExternalStorageDirectory().path}/custom2")
        newDirInsideInternalStorage.mkdir()
        val customDirInsideInternalStorage = org.videolan.vlc.mediadb.models.CustomDirectory(newDirInsideInternalStorage.path)
        coEvery { mockedDirectoryRepo.getCustomDirectories() } returns listOf(customDir, customDirInsideInternalStorage)
        initBrowserModel(false, null)

        // TODO: This test will fail because nested directories should not be shown.
        val testResult = browserModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
        assertEquals(context.getString(R.string.internal_memory), testResult[0].title)
    }

    @Test
    fun whenAtCustomDirAndHiddenDirectoryPresentWithHideHiddenFiles_checkResultHasCorrectDirectoriesAndFlagIsNotShowHiddenFiles() {
        val customDir = org.videolan.vlc.mediadb.models.CustomDirectory(temporaryFolder.root.path)
        coEvery { mockedDirectoryRepo.getCustomDirectories() } returns listOf(customDir)

        initBrowserModel(false, customDir.path)
        fillFilesInDataset(temporaryFolder.root)

        val testResult = browserModel.dataset.test()
                .value()

        assertEquals(countDirs, testResult.size)
        assertEquals(0, browserProvider.getFlags(false) and MediaBrowser.Flag.ShowHiddenFiles)
    }

    @Test
    fun whenAtCustomDirAndHiddenDirectoryPresentWithShowHiddenFiles_checkResultHasHiddenDirectoriesAndFlagIsShowHiddenFiles() {
        val customDir = org.videolan.vlc.mediadb.models.CustomDirectory(temporaryFolder.root.path)
        coEvery { mockedDirectoryRepo.getCustomDirectories() } returns listOf(customDir)

        initBrowserModel(true, customDir.path)
        fillFilesInDataset(temporaryFolder.root)

        val testResult = browserModel.dataset.test()
                .value()

        assertEquals(countDirs + countHiddenDirs, testResult.size)
        assertNotEquals(0, browserProvider.getFlags(false) and MediaBrowser.Flag.ShowHiddenFiles)
    }

    @Test
    fun whenAtInternalStorageAndSortedDescending_checkResultIsReversed() {
        initBrowserModel(false, Environment.getExternalStorageDirectory().path)
        fillFilesInDataset(temporaryFolder.root)

        val oldResult = browserModel.dataset.test()
                .value().toList()

        browserModel.sort(Medialibrary.SORT_ALPHA)

        val newResult = browserModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(oldResult.reversed(), newResult)
    }
}