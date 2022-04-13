package org.videolan.vlc.viewmodels

import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.jraska.livedata.test
import io.mockk.*
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.FileUtils
import org.videolan.vlc.BaseTest
import org.videolan.vlc.R
import org.videolan.vlc.database.ExternalSubDao
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.util.TestCoroutineContextProvider
import org.videolan.vlc.util.TestUtil
import org.videolan.vlc.util.applyMock
import java.io.IOException
import java.util.concurrent.TimeUnit

class SubtitlesModelTest : BaseTest() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @MockK
    private lateinit var mockedOpenSubRepo: OpenSubtitleRepository

    private val mockedDao: ExternalSubDao = mockk()
    private lateinit var mediaPath: String
    private val downloadedLiveData = MutableLiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>()

    private lateinit var subtitlesModel: SubtitlesModel

    init {
        val subRepo = ExternalSubRepository(mockedDao, TestCoroutineContextProvider())
        ExternalSubRepository.applyMock(subRepo)
        val capturedMedia = slot<String>()
        // To mock the behavior of actual get function in DAO (filter by media path)
        every { mockedDao.get(capture(capturedMedia)) } answers { Transformations.map(downloadedLiveData) { it.filter { it.mediaPath == capturedMedia.captured } } }

        // To use the mocked instance of OpenSubRepository
        OpenSubtitleRepository.instance = lazyOf(mockedOpenSubRepo)

        // Used when computing hash of media file.
        mockkObject(FileUtils) {
            every { FileUtils.computeHash(any()) } returns "fake_hash"
        }
    }

    override fun beforeTest() {
        super.beforeTest()
        mediaPath = temporaryFolder.newFile("fake_media").path
        subtitlesModel = SubtitlesModel(context, mediaPath.toUri(), coroutineContextProvider = TestCoroutineContextProvider())
    }

    @Test
    fun addFourDownloadedSubtitlesWithThreeCorrectMediaPath_checkHistoryHasSizeAsThree() {
        val inputSubs = (0..2).map {
            val subPath = temporaryFolder.newFile("sub$it").absolutePath
            TestUtil.createExternalSub(it.toString(), subPath, mediaPath, "en", "xyz$it")
        }.toMutableList()
        val subPath = temporaryFolder.newFile("sub3").absolutePath
        inputSubs.add(TestUtil.createExternalSub("3", subPath, "/wrong", "jp", "abc4"))

        downloadedLiveData.value = inputSubs

        val testResult = subtitlesModel.history.test()
                .awaitValue()
                .value()

        assertEquals(3, testResult.size)
    }

    @Test
    fun addThreeDownloadingSubtitlesWithTwoCorrectMediaPath_checkHistoryHasSizeAsTwo() {
        (0..1).map {
            ExternalSubRepository.getInstance(context).addDownloadingItem(
                    it.toLong(), TestUtil.createDownloadingSubtitleItem("$it", mediaPath, "en", "xyz", "abc.com/$it")
            )
        }
        ExternalSubRepository.getInstance(context).addDownloadingItem(
                2, TestUtil.createDownloadingSubtitleItem("2", "/wrong", "en", "xyz", "abc.com/2")
        )

        val testResult = subtitlesModel.history.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
    }

    @Test
    fun addTwoDownloadingSubtitlesAndTwoDownloadedSubtitles_checkHistoryHasSizeAsFour() {
        (0..1).map {
            ExternalSubRepository.getInstance(context).addDownloadingItem(
                    it.toLong(), TestUtil.createDownloadingSubtitleItem("$it", mediaPath, "en", "xyz", "abc.com/$it")
            )
        }
        val inputSubs = (0..1).map {
            val subPath = temporaryFolder.newFile("sub$it").absolutePath
            TestUtil.createExternalSub(it.toString(), subPath, mediaPath, "en", "xyz$it")
        }.toList()

        downloadedLiveData.value = inputSubs

        val testResult = subtitlesModel.history.test()
                .awaitValue()
                .value()

        assertEquals(4, testResult.size)
    }

    @Test
    fun searchByNameAndNoResultHasFound_checkResultIsEmpty() {
        coEvery { mockedOpenSubRepo.queryWithName(any(), any(), any(), any<List<String>>()) } returns emptyList()

        subtitlesModel.observableSearchName.set("abc")
        subtitlesModel.search(false)

        subtitlesModel.result.test()
                .awaitValue()
                .assertValue { it.isEmpty() }

        assertEquals(context.getString(R.string.no_result), subtitlesModel.observableMessage.get())
    }

    @Test
    fun searchByNameAndTwoResultHasFound_checkResultHasSizeAsTwo() {
        val openSubs = (0..1).map { TestUtil.createOpenSubtitle("$it", "en", "xyz", "abc.com/$it") }
        coEvery { mockedOpenSubRepo.queryWithName(any(), any(), any(), any<List<String>>()) } returns openSubs

        subtitlesModel.observableSearchName.set("abc")
        subtitlesModel.search(false)

        val testResult = subtitlesModel.result.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
        assertEquals(testResult[0].state, State.NotDownloaded)
        assertEquals(testResult[1].state, State.NotDownloaded)
    }

    @Test
    fun searchByNameAndThreeResultHasFoundTwoAreInHistory_checkResultHasSizeAsThreeAndCorrectStates() {
        val openSubs = (0..2).map { TestUtil.createOpenSubtitle("$it", "en", "xyz", "abc.com/$it") }
        coEvery { mockedOpenSubRepo.queryWithName(any(), any(), any(), any<List<String>>()) } returns openSubs

        ExternalSubRepository.getInstance(context).addDownloadingItem(
                0L, TestUtil.createDownloadingSubtitleItem("0", mediaPath, "en", "xyz", "abc.com/0")
        )
        val subPath = temporaryFolder.newFile("sub1").absolutePath
        downloadedLiveData.value = listOf(TestUtil.createExternalSub("1", subPath, mediaPath, "en", "xyz"))

        subtitlesModel.observableSearchName.set("abc")
        subtitlesModel.search(false)

        subtitlesModel.history.test()
                .awaitValue()

        val testResult = subtitlesModel.result.test()
                .awaitValue()
                .value()

        assertEquals(3, testResult.size)
        assertEquals(testResult[0].state, State.Downloading)
        assertEquals(testResult[1].state, State.Downloaded)
        assertEquals(testResult[2].state, State.NotDownloaded)
    }

    @Test
    fun addTwoDownloadedSubtitlesAndDeleteTwo_verifyDaoDeleteCalled() {
        val inputSubs = (0..1).map {
            val subPath = temporaryFolder.newFile("sub$it").absolutePath
            TestUtil.createExternalSub(it.toString(), subPath, mediaPath, "en", "xyz$it")
        }.toMutableList()

        every { mockedDao.delete(any(), any()) } just runs

        downloadedLiveData.value = inputSubs

        (0..1).map { subtitlesModel.deleteSubtitle(mediaPath, "$it") }

        verify(exactly = 2) { mockedDao.delete(any(), any()) }
    }

    @Test
    fun searchByHashAndNoResultHasFound_checkResultIsEmpty() {
        coEvery { mockedOpenSubRepo.queryWithHash(any(), any(), any<List<String>>()) } returns emptyList()

        subtitlesModel.search(true)

        subtitlesModel.result.test()
                .awaitValue()
                .assertValue { it.isEmpty() }

        assertEquals(context.getString(R.string.no_result), subtitlesModel.observableMessage.get())
    }

    @Test
    fun searchByHashAndTwoResultHasFound_checkResultHasSizeAsTwo() {
        val openSubs = (0..1).map { TestUtil.createOpenSubtitle("$it", "en", "xyz", "abc.com/$it") }
        coEvery { mockedOpenSubRepo.queryWithHash(any(), any(), any<List<String>>()) } returns openSubs

        subtitlesModel.search(true)

        val testResult = subtitlesModel.result.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
        assertEquals(testResult[0].state, State.NotDownloaded)
        assertEquals(testResult[1].state, State.NotDownloaded)
    }

    @Test
    fun searchByNameWithNoConnection_checkValidMessage() {
        coEvery { mockedOpenSubRepo.queryWithName(any(), any(), any(), any<List<String>>()) } throws NoConnectivityException()

        subtitlesModel.observableSearchName.set("abc")
        subtitlesModel.search(false)

        subtitlesModel.result.test()
                .awaitValue(3, TimeUnit.SECONDS)
                .assertValue { it.isEmpty() }

        assertEquals(context.getString(R.string.no_internet_connection), subtitlesModel.observableMessage.get())
    }

    @Test
    fun searchByNameWithConverterError_checkValidMessage() {
        coEvery { mockedOpenSubRepo.queryWithName(any(), any(), any(), any<List<String>>()) } throws IOException()

        subtitlesModel.observableSearchName.set("abc")
        subtitlesModel.search(false)

        subtitlesModel.result.test()
                .awaitValue(3, TimeUnit.SECONDS)
                .assertValue { it.isEmpty() }

        assertEquals(context.getString(R.string.subs_download_error), subtitlesModel.observableMessage.get())
    }
}