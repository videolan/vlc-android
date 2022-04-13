package org.videolan.vlc.viewmodels.browser

import android.os.Handler
import com.jraska.livedata.test
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.stubs.StubMedia
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.vlc.BaseTest
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.tools.CoroutineContextProvider
import org.videolan.vlc.util.TestCoroutineContextProvider
import java.io.File

class FilePickerModelTest : BaseTest() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockedLibVlc: ILibVLC = mockk(relaxed = true)
    private lateinit var dummyUrl: String
    private lateinit var mediaBrowser: MediaBrowser

    private lateinit var browserModel: BrowserModel
    private lateinit var browserProvider: BrowserProvider

    private val countVideos = 2
    private val countDirs = 4

    init {
        // BrowserHandler mocked.
        val handler: Handler = mockk()

        BrowserProvider.overrideCreator = false
        BrowserProvider.registerCreator {
            mediaBrowser = spyk(MediaBrowser(mockedLibVlc, null, handler))
            mediaBrowser
        }
        BrowserProvider.registerCreator(clazz = CoroutineContextProvider::class.java) { org.videolan.vlc.util.TestCoroutineContextProvider() }
    }

    override fun beforeTest() {
        super.beforeTest()
        dummyUrl = temporaryFolder.root.absolutePath

        browserModel = BrowserModel(application, dummyUrl, TYPE_PICKER, false, true, org.videolan.vlc.util.TestCoroutineContextProvider())
        browserProvider = browserModel.provider

        setupTestFiles()
    }

    private fun setupTestFiles() {
        (0 until countDirs).map { temporaryFolder.newFile("dir$it") }
        (0 until countVideos).map { temporaryFolder.newFile("video$it.mp4") }
    }

    private fun addFileToProvider(i: Int, file: File) {
        val t = StubMedia(mockedLibVlc, "file://${file.path}").apply { if (!file.name.endsWith(".mp4")) type = IMedia.Type.Directory }
        browserProvider.addMedia(StubMediaWrapper(t))
    }

    @Test
    fun whenBrowseRootAndRootHasFiles_getListOfDirectories() {
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)

        val testResult = browserModel.dataset.test()
                .value()

        assertEquals(countDirs, testResult.size)
        assert(testResult[0].title.startsWith("dir"))
    }

    @Test
    fun whenBrowseRootAndRootEmpty_checkTheFolderIsEmpty() {

        browserModel.dataset.test()
                .assertValue { it.isEmpty() }
    }

    @Test
    fun whenBrowseRootAndBrowseEndEventTriggered_ensureLoadingIsSetToFalse() {
        browserModel.loading.test()
                .assertValue(true)

        browserModel.loading.test()
                .awaitValue()
                .assertValue(false)
    }

    @Test
    fun whenRootHasFilesAndRefreshCalled_getUpdatedListOfDirectories() {
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)

        var result = browserModel.dataset.test()
                .value()

        assertEquals(countDirs, result.size)

        browserModel.refresh()

        temporaryFolder.newFile("dir${countDirs + 1}")
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)

        result = browserModel.dataset.test()
                .value()

        assertEquals(countDirs + 1, result.size)
    }
}