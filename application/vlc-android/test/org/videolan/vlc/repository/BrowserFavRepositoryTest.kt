/*******************************************************************************
 *  BrowserFavRepositoryTest.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 ******************************************************************************/

package org.videolan.vlc.repository

import android.net.Uri
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.videolan.vlc.ExternalMonitor


@RunWith(PowerMockRunner::class)
@PrepareForTest(Uri::class, ExternalMonitor::class)
class BrowserFavRepositoryTest {
//
//    @Rule
//    @JvmField
//    //To prevent Method getMainLooper in android.os.Looper not mocked error when setting value for MutableLiveData
//    val instantExecutorRule = InstantTaskExecutorRule()
//
//    private val browserFavDao: BrowserFavDao = mock()
//    private lateinit var browserFavRepository: BrowserFavRepository
//    @Before
//    fun init() {
//        val db = mock<MediaDatabase>()
//        `when`(db.browserFavDao()).thenReturn(browserFavDao)
//        browserFavRepository = BrowserFavRepository(browserFavDao)
//    }
//
//    @Test fun addTwoLocalFavAndTwoNetworkFav_GetShouldReturnFour() = runBlocking {
//        PowerMockito.mockStatic(Uri::class.java)
//        val mockedLocalUri1 = mock<Uri>()
//        val mockedLocalUri2 = mock<Uri>()
//        val mockedNetworkUri1 = mock<Uri>()
//        val mockedNetworkUri2 = mock<Uri>()
//
//        val fakeLocalUris = TestUtil.createLocalUris(2)
//        val fakeNetworkUris = TestUtil.createNetworkUris(2)
//
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[0]))
//                .thenReturn(mockedLocalUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[1]))
//                .thenReturn(mockedLocalUri2)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[0]))
//                .thenReturn(mockedNetworkUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[1]))
//                .thenReturn(mockedNetworkUri2)
//
//
//        val fakeLocalFavs = fakeLocalUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addLocalFavItem(parsedUri, "local$index", null).join()
//            TestUtil.createLocalFav(parsedUri, "local$index", null)
//        }
//
//        val fakeNetworkFavs = fakeNetworkUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addNetworkFavItem(parsedUri, "network$index", null).join()
//            TestUtil.createNetworkFav(parsedUri, "network$index", null)
//        }
//
//        val inserted = argumentCaptor<org.videolan.vlc.mediadb.models.BrowserFav>()
//        verify(browserFavDao, times(4)).insert(
//                inserted.capture() ?: /*Just to prevent must not null in kotlin*/uninitialized()
//        )
//        assertThat(inserted.allValues.size, `is`(4))
//        assertThat(inserted.allValues[0], `is`(fakeLocalFavs[0]))
//        assertThat(inserted.allValues[1], `is`(fakeLocalFavs[1]))
//        assertThat(inserted.allValues[2], `is`(fakeNetworkFavs[0]))
//        assertThat(inserted.allValues[3], `is`(fakeNetworkFavs[1]))
//
//        val dbData = MutableLiveData<List<org.videolan.vlc.mediadb.models.BrowserFav>>()
//        dbData.value = fakeLocalFavs + fakeNetworkFavs
//        `when`(browserFavDao.getAll()).thenReturn(dbData)
//        val browserFavorites = getValue(browserFavRepository.browserFavorites)
//        assertThat(browserFavorites.size, `is`(4))
//        assertThat(browserFavorites, hasItem(fakeLocalFavs[0]))
//        assertThat(browserFavorites, hasItem(fakeLocalFavs[1]))
//        assertThat(browserFavorites, hasItem(fakeNetworkFavs[0]))
//        assertThat(browserFavorites, hasItem(fakeNetworkFavs[1]))
//    }
//
//    @Test fun addTwoLocalFavAndTwoNetworkFav_GetLocalShouldReturnTwo() = runBlocking {
//        PowerMockito.mockStatic(Uri::class.java)
//        val mockedLocalUri1 = mock<Uri>()
//        val mockedLocalUri2 = mock<Uri>()
//        val mockedNetworkUri1 = mock<Uri>()
//        val mockedNetworkUri2 = mock<Uri>()
//
//        val fakeLocalUris = TestUtil.createLocalUris(2)
//        val fakeNetworkUris = TestUtil.createNetworkUris(2)
//
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[0]))
//                .thenReturn(mockedLocalUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[1]))
//                .thenReturn(mockedLocalUri2)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[0]))
//                .thenReturn(mockedNetworkUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[1]))
//                .thenReturn(mockedNetworkUri2)
//
//
//        val fakeLocalFavs = fakeLocalUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addLocalFavItem(parsedUri, "local$index", null).join()
//            TestUtil.createLocalFav(parsedUri, "local$index", null)
//        }
//
//        val fakeNetworkFavs = fakeNetworkUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addNetworkFavItem(parsedUri, "network$index", null).join()
//            TestUtil.createNetworkFav(parsedUri, "network$index", null)
//        }
//
//        val inserted = argumentCaptor<org.videolan.vlc.mediadb.models.BrowserFav>()
//        verify(browserFavDao, times(4)).insert(
//                inserted.capture() ?: /*Just to prevent must not null in kotlin*/uninitialized()
//        )
//        assertThat(inserted.allValues.size, `is`(4))
//        assertThat(inserted.allValues[0], `is`(fakeLocalFavs[0]))
//        assertThat(inserted.allValues[1], `is`(fakeLocalFavs[1]))
//        assertThat(inserted.allValues[2], `is`(fakeNetworkFavs[0]))
//        assertThat(inserted.allValues[3], `is`(fakeNetworkFavs[1]))
//
//        val dbData = MutableLiveData<List<org.videolan.vlc.mediadb.models.BrowserFav>>()
//        dbData.value = fakeLocalFavs
//        `when`(browserFavDao.getAllLocalFavs()).thenReturn(dbData)
//        val localFavorites = getValue(browserFavRepository.localFavorites)
//        assertThat(localFavorites.size, `is`(2))
//        assertThat(localFavorites, hasItem(fakeLocalFavs[0]))
//        assertThat(localFavorites, hasItem(fakeLocalFavs[1]))
//    }
//
//
//    // Testing browserFavRepository.networkFavorites is not Easy, because it needs to mock the ExternalMonitor
//    // and MediaWrapperImpl. ExternalMonitor uses Handler(Looper.getMainLooper()) that makes mocking harder
//    // So I comment this for now until find a proper solution for that
//
//
////    @Test fun addTwoLocalFavAndTwoNetworkFav_GetNetworkShouldReturnTwo() = runBlocking {
////        PowerMockito.mockStatic(Uri::class.java)
////        val mockedLocalUri1 = mock<Uri>()
////        val mockedLocalUri2 = mock<Uri>()
////        val mockedNetworkUri1 = mock<Uri>()
////        val mockedNetworkUri2 = mock<Uri>()
////
////        val fakeLocalUris = TestUtil.createLocalUris(2)
////        val fakeNetworkUris = TestUtil.createNetworkUris(2)
////
////        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[0]))
////                .thenReturn(mockedLocalUri1)
////        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[1]))
////                .thenReturn(mockedLocalUri2)
////        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[0]))
////                .thenReturn(mockedNetworkUri1)
////        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[1]))
////                .thenReturn(mockedNetworkUri2)
////
////
////        val fakeLocalFavs = fakeLocalUris.mapIndexed {index, uri ->
////            val parsedUri = Uri.parse((uri))
////            browserFavRepository.addLocalFavItem(parsedUri, "local$index", null).join()
////            TestUtil.createLocalFav(parsedUri, "local$index", null)
////        }
////
////        val fakeNetworkFavs = fakeNetworkUris.mapIndexed {index, uri ->
////            val parsedUri = Uri.parse((uri))
////            browserFavRepository.addNetworkFavItem(parsedUri, "network$index", null).join()
////            TestUtil.createNetworkFav(parsedUri, "network$index", null)
////        }
////
////        val inserted = argumentCaptor<BrowserFav>()
////        verify(browserFavDao, times(4)).insert(
////                inserted.capture() ?: /*Just to prevent must not null in kotlin*/uninitialized()
////        )
////        assertThat(inserted.allValues.size, `is`(4))
////        assertThat(inserted.allValues[0], `is`(fakeLocalFavs[0]))
////        assertThat(inserted.allValues[1], `is`(fakeLocalFavs[1]))
////        assertThat(inserted.allValues[2], `is`(fakeNetworkFavs[0]))
////        assertThat(inserted.allValues[3], `is`(fakeNetworkFavs[1]))
////
////        PowerMockito.mockStatic(ExternalMonitor::class.java)
////        PowerMockito.`when`<Any>(ExternalMonitor::class.java, "isConnected").thenReturn(true)
////        PowerMockito.`when`<Any>(ExternalMonitor::class.java, "allowLan").thenReturn(true)
////
////        val dbData = MutableLiveData<List<BrowserFav>>()
////        dbData.value = fakeNetworkFavs
////        `when`(browserFavDao.getAllNetwrokFavs()).thenReturn(dbData)
////        val networkFavorites = getValue(browserFavRepository.networkFavorites)
////        assertThat(networkFavorites.size, `is`(2))
////        assertThat(networkFavorites, hasItem(fakeLocalFavs[0]))
////        assertThat(networkFavorites, hasItem(fakeLocalFavs[1]))
////    }
//
//
//    @Test fun addTwoLocalFavAndTwoNetworkFav_DeleteOneLocal() = runBlocking {
//        PowerMockito.mockStatic(Uri::class.java)
//        val mockedLocalUri1 = mock<Uri>()
//        val mockedLocalUri2 = mock<Uri>()
//        val mockedNetworkUri1 = mock<Uri>()
//        val mockedNetworkUri2 = mock<Uri>()
//
//        val fakeLocalUris = TestUtil.createLocalUris(2)
//        val fakeNetworkUris = TestUtil.createNetworkUris(2)
//
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[0]))
//                .thenReturn(mockedLocalUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeLocalUris[1]))
//                .thenReturn(mockedLocalUri2)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[0]))
//                .thenReturn(mockedNetworkUri1)
//        PowerMockito.`when`<Any>(Uri::class.java, "parse", eq(fakeNetworkUris[1]))
//                .thenReturn(mockedNetworkUri2)
//
//
//        val fakeLocalFavs = fakeLocalUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addLocalFavItem(parsedUri, "local$index", null).join()
//            TestUtil.createLocalFav(parsedUri, "local$index", null)
//        }
//
//        val fakeNetworkFavs = fakeNetworkUris.mapIndexed {index, uri ->
//            val parsedUri = Uri.parse((uri))
//            browserFavRepository.addNetworkFavItem(parsedUri, "network$index", null).join()
//            TestUtil.createNetworkFav(parsedUri, "network$index", null)
//        }
//
//        browserFavRepository.deleteBrowserFav(fakeLocalFavs[0].uri).join()
//        verify(browserFavDao).delete(ArgumentMatchers.any(Uri::class.java) ?: uninitialized())
//
//
//    }
}