package org.videolan.vlc.viewmodels

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.jraska.livedata.test
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.modules.junit4.PowerMockRunnerDelegate
import org.robolectric.RobolectricTestRunner
import org.videolan.libvlc.LibVLC
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.util.RoboLiteTestRunner
import org.videolan.vlc.util.TestUtil
import org.videolan.vlc.util.mock
import java.io.File
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.powermock.modules.junit4.rule.PowerMockRule
import org.robolectric.annotation.Config


//@RunWith(RoboLiteTestRunner::class)
@RunWith(PowerMockRunner::class)
//@PowerMockRunnerDelegate(RobolectricTestRunner::class)
//@Config(sdk=[21])
@PrepareForTest(value = [Medialibrary::class, LibVLC::class, System::class, Uri::class, TextUtils::class])
@PowerMockIgnore(value = ["javax.management.*", "org.apache.http.conn.ssl.*", "com.amazonaws.http.conn.ssl.*", "javax.net.ssl.*", "androidx.*"])
class StreamsModelTest {

    private val mockedLibrary: Medialibrary = PowerMockito.spy(Medialibrary())

    private val context: Context = mock()

    private lateinit var streamsModel: StreamsModel

//    @Rule
//    @JvmField
//    val powerMockRule = PowerMockRule()

    @Rule
    @JvmField
    //To prevent Method getMainLooper in android.os.Looper not mocked error when setting value for MutableLiveData
    val instantExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        `when`(context.getExternalFilesDir(any())).thenReturn(File("./"))
        `when`(context.getDir(any(), anyInt())).thenReturn(File("./"))

        PowerMockito.mockStatic(Medialibrary::class.java)
        PowerMockito.mockStatic(System::class.java)
        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.mockStatic(TextUtils::class.java)

        PowerMockito.suppress(PowerMockito.method(System::class.java, "loadLibrary", String::class.java))
        PowerMockito.`when`(Medialibrary.getInstance()).thenReturn(mockedLibrary)

        Dispatchers.setMain(Dispatchers.Unconfined)
        streamsModel = StreamsModel(context)
    }

    @Test
    fun failedInitialization_GetEmptyCollection() {
        PowerMockito.doReturn(Medialibrary.ML_INIT_FAILED).`when`(mockedLibrary, "nativeInit", any(), any())

        mockedLibrary.init(context)

        val testResult: MutableList<MediaWrapper> = mutableListOf()
        streamsModel.refresh()
        streamsModel.dataset
                .test()
                .awaitValue()
                .assertHasValue()
                .assertValue(testResult)
    }

    @Test
    fun addTwoMediaHistory_GetTwoPlayedMediaStreams() {
        // Setup
        val mockedUri1: Uri = mock()
        val mockedUri2: Uri = mock()

        val fakeMediaStrings = TestUtil.createNetworkUris(2)

        `when`(Uri.parse(eq(fakeMediaStrings[0]))).thenReturn(mockedUri1)
        `when`(mockedUri1.lastPathSegment).thenReturn(fakeMediaStrings[0])
        `when`(mockedUri1.toString()).thenReturn(fakeMediaStrings[0])
        `when`(Uri.parse(eq(fakeMediaStrings[1]))).thenReturn(mockedUri2)
        `when`(mockedUri2.lastPathSegment).thenReturn(fakeMediaStrings[1])
        `when`(mockedUri2.toString()).thenReturn(fakeMediaStrings[1])

        val fakeMedias = fakeMediaStrings.map { s -> MediaWrapper(Uri.parse(s)) }
        val result = fakeMedias.toTypedArray()

        PowerMockito.doReturn(Medialibrary.ML_INIT_SUCCESS).`when`(mockedLibrary, "nativeInit", any(), any())
        PowerMockito.doReturn(result).`when`(mockedLibrary, "nativeLastStreamsPlayed")

        // Execution
        mockedLibrary.init(context)
        streamsModel.refresh()

        // Assertions
        val getResult = streamsModel.dataset
                .test()
                .awaitValue()
                .assertHasValue()
                .value()

        assertEquals(2, getResult.size)
        assertEquals(result[0], getResult[0])
        assertEquals(result[1], getResult[1])
    }
}