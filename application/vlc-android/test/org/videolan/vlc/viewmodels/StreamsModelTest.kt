package org.videolan.vlc.viewmodels

import androidx.core.net.toUri
import com.jraska.livedata.test
import io.mockk.every
import io.mockk.slot
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.vlc.BaseTest
import org.videolan.vlc.util.TestCoroutineContextProvider
import org.videolan.vlc.util.TestUtil


class StreamsModelTest : BaseTest() {
    private val mediaLibrary: Medialibrary = Medialibrary.getInstance()
    private lateinit var streamsModel: StreamsModel

    override fun beforeTest() {
        super.beforeTest()
        mediaLibrary.clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
        streamsModel = StreamsModel(application, TestCoroutineContextProvider())
    }

    @Test
    fun whenRefreshCalled_ListIsUpdated() {
        val fakeMediaStrings = TestUtil.createNetworkUris(2)

        streamsModel.dataset.test()
                .awaitValue()
                .assertValue(Medialibrary.EMPTY_COLLECTION.toMutableList())

        val result = fakeMediaStrings.map {
            val media = MediaWrapperImpl(it.toUri())
            println(mediaLibrary.addToHistory(media.location, media.title))
            media
        }

        streamsModel.refresh()

        val testResult = streamsModel.dataset
                .test()
                .awaitValue()
                .assertHasValue()
                .value()

        assertEquals(result.size, testResult.size)
        assertEquals(result[0], testResult[0])
        assertEquals(result[1], testResult[1])
    }

    @Test
    fun whenRenameCalledAtPos_MediaTitleIsUpdated() {
        val pos = 0
        val fakeMediaStrings = TestUtil.createNetworkUris(2)

        val argumentName = slot<String>()

        val result = fakeMediaStrings.map {
            val media = spyk(MediaWrapperImpl(it.toUri()))
            mediaLibrary.addToHistory(media.location, media.title)
            media
        }
        val oldMediaTitle = result[pos].title

        val media = result[pos]

        every { media.rename(capture(argumentName)) } answers { media.setDisplayTitle(argumentName.captured) }

        streamsModel.refresh()

        streamsModel.dataset.test()
                .awaitValue()
//                .assertValue(result.toMutableList())

        val newMediaTitle = "$oldMediaTitle~new"

        streamsModel.rename(pos, newMediaTitle)

        val testResult = streamsModel.dataset.test()
                .awaitValue()
                .assertHasValue()
                .value()

        assertEquals(newMediaTitle, testResult[pos].title)
    }
}