package org.videolan.vlc.viewmodels

import androidx.core.net.toUri
import com.jraska.livedata.test
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Test
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.BaseTest
import org.videolan.vlc.util.TestCoroutineContextProvider
import org.videolan.vlc.util.TestUtil

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class HistoryModelTest : BaseTest() {
    private val mediaLibrary: Medialibrary = Medialibrary.getInstance()
    private lateinit var historyModel: HistoryModel

    override fun beforeTest() {
        super.beforeTest()
        mediaLibrary.clearHistory()
        historyModel = HistoryModel(context, org.videolan.vlc.util.TestCoroutineContextProvider())
    }

    @Test
    fun whenRefreshCalled_ListIsUpdated() {
        val fakeMediaStrings = org.videolan.vlc.util.TestUtil.createLocalUris(2)

        historyModel.refresh()

        historyModel.dataset.test()
                .awaitValue()
                .assertValue(Medialibrary.EMPTY_COLLECTION.toMutableList())

        val result = fakeMediaStrings.map {
            val media = MLServiceLocator.getAbstractMediaWrapper(it.toUri())
            mediaLibrary.addToHistory(media.location, media.title)
            media
        }

        historyModel.refresh()

        val testResult = historyModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(2, testResult.size)
        assertEquals(result[0], testResult[0])
        assertEquals(result[1], testResult[1])
    }

    @Test
    fun whenListHasTwoItemsAndLastIsMovedUp_ListHasUpdatedItemsOrder() {
        val fakeMediaStrings = org.videolan.vlc.util.TestUtil.createLocalUris(2)

        val result = fakeMediaStrings.map {
            val media = MLServiceLocator.getAbstractMediaWrapper(it.toUri()).apply { type = MediaWrapper.TYPE_VIDEO }
            mediaLibrary.addToHistory(media.location, media.title)
            media
        }

        historyModel.refresh()

        historyModel.moveUp(result[1])

        val testResult = historyModel.dataset.test()
                .awaitValue()
                .value()

        assertEquals(result.size, testResult.size)
        assertEquals(result[0], testResult[1])
        assertEquals(result[1], testResult[0])
    }
}