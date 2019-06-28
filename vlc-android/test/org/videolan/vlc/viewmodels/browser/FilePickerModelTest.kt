package org.videolan.vlc.viewmodels.browser

import android.os.Handler
import com.jraska.livedata.test
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.stubs.StubMedia
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.vlc.BaseTest
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.util.CoroutineContextProvider
import org.videolan.vlc.util.TestCoroutineContextProvider
import java.io.File

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FilePickerModelTest : BaseTest() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockedLibVlc: LibVLC = mockk(relaxed = true)
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
            mediaBrowser = spyk(MediaBrowser(mockedLibVlc, it, handler))
            mediaBrowser
        }
        BrowserProvider.registerCreator(clazz = CoroutineContextProvider::class.java) { TestCoroutineContextProvider() }
    }

    override fun beforeTest() {
        super.beforeTest()
        dummyUrl = temporaryFolder.root.absolutePath

        browserModel = BrowserModel(application, dummyUrl, TYPE_PICKER, false, true, TestCoroutineContextProvider())
        browserProvider = browserModel.provider

        setupTestFiles()
    }

    private fun setupTestFiles() {
        (1..countDirs).map { temporaryFolder.newFile("dir$it") }
        (1..countVideos).map { temporaryFolder.newFile("video$it.mp4") }
    }

    private fun addFileToProvider(i: Int, file: File) {
        val t = StubMedia(mockedLibVlc, "file://${file.path}").apply { if (!file.name.endsWith(".mp4")) type = IMedia.Type.Directory }
        browserProvider.onMediaAdded(i, t)
    }

    @Test
    fun whenBrowseRootAndRootHasFiles_getListOfDirectories() {
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)
        browserProvider.onBrowseEnd()

        val testResult = browserModel.dataset.test()
                .value()

        assertEquals(countDirs, testResult.size)
        assert(testResult[0].title.startsWith("dir"))
    }

    @Test
    fun whenBrowseRootAndRootEmpty_checkTheFolderIsEmpty() {
        browserProvider.onBrowseEnd()

        browserModel.dataset.test()
                .assertValue { it.isEmpty() }
    }

    @Test
    fun whenBrowseRootAndBrowseEndEventTriggered_ensureLoadingIsSetToFalse() {
        browserModel.loading.test()
                .assertValue(true)

        browserProvider.onBrowseEnd()

        browserModel.loading.test()
                .awaitValue()
                .assertValue(false)
    }

    @Test
    fun whenRootHasFilesAndRefreshCalled_getUpdatedListOfDirectories() {
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)
        browserProvider.onBrowseEnd()

        var result = browserModel.dataset.test()
                .value()

        assertEquals(countDirs, result.size)

        browserModel.refresh()

        temporaryFolder.newFile("dir${countDirs + 1}")
        temporaryFolder.root.listFiles().mapIndexed(this::addFileToProvider)
        browserProvider.onBrowseEnd()

        result = browserModel.dataset.test()
                .value()

        // TODO: This will fail because the refresh behavior is buggy of BrowserProvider subclasses.
        assertEquals(countDirs + 1, result.size)
    }
}