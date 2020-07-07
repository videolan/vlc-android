/*******************************************************************************
 *  ExternalSubRepositoryTest.kt
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
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.videolan.vlc.database.ExternalSubDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.util.*


@RunWith(PowerMockRunner::class)
@PrepareForTest(Uri::class)
class ExternalSubRepositoryTest {
    private val externalSubDao = mock<ExternalSubDao>()
    private lateinit var externalSubRepository: ExternalSubRepository

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    var temp = TemporaryFolder()

    @Before fun init() {
        val db = mock<MediaDatabase>()
        `when`(db.externalSubDao()).thenReturn(externalSubDao)
        externalSubRepository = ExternalSubRepository(externalSubDao)
    }

    @Test fun saveTwoSubtitleForTwoMedia_GetShouldReturnZero() = runBlocking {
        val foo = "/storage/emulated/foo.mkv"
        val bar = "/storage/emulated/bar.mkv"

        val fakeFooSubtitles = TestUtil.createExternalSubsForMedia(foo, "foo", 2)
        val fakeBarSubtitles = TestUtil.createExternalSubsForMedia(bar, "bar", 2)

        fakeFooSubtitles.forEach {
            externalSubRepository.saveDownloadedSubtitle(it.idSubtitle, it.subtitlePath, it.mediaPath, it.subLanguageID, it.movieReleaseName)
        }

        fakeBarSubtitles.forEach {
            externalSubRepository.saveDownloadedSubtitle(it.idSubtitle, it.subtitlePath, it.mediaPath, it.subLanguageID, it.movieReleaseName)
        }


        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.`when`<Any>(Uri::class.java, "decode", anyString()).thenAnswer { it.arguments[0] as String }

        val inserted = argumentCaptor<org.videolan.vlc.mediadb.models.ExternalSub>()
        verify(externalSubDao, times(4)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(4))
        assertThat(inserted.allValues, hasItem(fakeFooSubtitles[0]))
        assertThat(inserted.allValues, hasItem(fakeFooSubtitles[1]))
        assertThat(inserted.allValues, hasItem(fakeBarSubtitles[0]))
        assertThat(inserted.allValues, hasItem(fakeBarSubtitles[1]))

        val fakeFooLiveDataSubtitles = MutableLiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>()
        val fakeBarLiveDataSubtitles = MutableLiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>()
        fakeFooLiveDataSubtitles.value = fakeFooSubtitles
        fakeBarLiveDataSubtitles.value = fakeBarSubtitles
        `when`(externalSubDao.get(foo)).thenReturn(fakeFooLiveDataSubtitles)
        `when`(externalSubDao.get(bar)).thenReturn(fakeBarLiveDataSubtitles)

        val fooSubtitles = getValue(externalSubRepository.getDownloadedSubtitles(foo.toUri()))
        verify(externalSubDao, times(2)).get(ArgumentMatchers.anyString())
        assertThat(fooSubtitles.size, `is`(0))
    }


    @Test fun saveTwoSubtitleForTwoMediaCreateTemporaryFilesForThem_GetShouldReturnTwoForEach() {
        val foo = "/storage/emulated/foo.mkv"
        val bar = "/storage/emulated/bar.mkv"

        val fakeFooSubtitles = (0 until 2).map {
            val file = temp.newFile("foo.$it.srt")
            externalSubRepository.saveDownloadedSubtitle("1$it", file.path, foo, "en", "foo" )
            TestUtil.createExternalSub("1$it", file.path, foo, "en", "foo")
        }

        val fakeBarSubtitles = (0 until 2).map {
            val file = temp.newFile("bar.$it.srt")
            externalSubRepository.saveDownloadedSubtitle("2$it", file.path, bar, "en", "bar")
            TestUtil.createExternalSub("2$it", file.path, bar, "en", "bar")
        }

        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.`when`<Any>(Uri::class.java, "decode", anyString()).thenAnswer { it.arguments[0] as String }

        val inserted = argumentCaptor<org.videolan.vlc.mediadb.models.ExternalSub>()
        verify(externalSubDao, times(4)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(4))
        assertThat(inserted.allValues, hasItem(fakeFooSubtitles[0]))
        assertThat(inserted.allValues, hasItem(fakeFooSubtitles[1]))
        assertThat(inserted.allValues, hasItem(fakeBarSubtitles[0]))
        assertThat(inserted.allValues, hasItem(fakeBarSubtitles[1]))

        val fakeFooLiveDataSubtitles = MutableLiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>()
        val fakeBarLiveDataSubtitles = MutableLiveData<List<org.videolan.vlc.mediadb.models.ExternalSub>>()
        fakeFooLiveDataSubtitles.value = fakeFooSubtitles
        fakeBarLiveDataSubtitles.value = fakeBarSubtitles

        `when`(externalSubDao.get(foo)).thenReturn(fakeFooLiveDataSubtitles)
        `when`(externalSubDao.get(bar)).thenReturn(fakeBarLiveDataSubtitles)

        val fooSubtitles = getValue(externalSubRepository.getDownloadedSubtitles(foo.toUri()))
        val barSubtitles = getValue(externalSubRepository.getDownloadedSubtitles(bar.toUri()))
        verify(externalSubDao, times(2)).get(ArgumentMatchers.anyString())
        assertThat(fooSubtitles.size, `is`(2))
        assertThat(barSubtitles.size, `is`(2))

        assertThat(fooSubtitles, hasItem(fakeFooSubtitles[0]))
        assertThat(fooSubtitles, hasItem(fakeFooSubtitles[1]))
        assertThat(barSubtitles, hasItem(fakeBarSubtitles[0]))
        assertThat(barSubtitles, hasItem(fakeBarSubtitles[1]))
    }

}
