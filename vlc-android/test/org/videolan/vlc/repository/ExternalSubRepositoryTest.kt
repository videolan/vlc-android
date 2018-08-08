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

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.net.Uri
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.videolan.vlc.database.BrowserFavDao
import org.videolan.vlc.database.ExternalSubDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.util.TestUtil
import org.videolan.vlc.util.argumentCaptor
import org.videolan.vlc.util.mock
import org.videolan.vlc.util.uninitialized
import org.junit.rules.TemporaryFolder


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

    @Test fun saveTwoSubtitleForTwoMedia_GetShouldReturnZero() {
        val foo = "foo.mkv"
        val bar = "bar.mkv"

        val fakeFooSubtitles = TestUtil.createExternalSubsForMedia(foo, 2)
        val fakeBarSubtitles = TestUtil.createExternalSubsForMedia(bar, 2)

        fakeFooSubtitles.forEach {
            externalSubRepository.saveSubtitle(it.uri, foo)
        }

        fakeBarSubtitles.forEach {
            externalSubRepository.saveSubtitle(it.uri, bar)
        }


        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.`when`<Any>(Uri::class.java, "decode", anyString()).thenAnswer { it.arguments[0] as String }

        val inserted = argumentCaptor<ExternalSub>()
        verify(externalSubDao, times(4)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(4))
        assertThat(inserted.allValues[0], `is`(fakeFooSubtitles[0]))
        assertThat(inserted.allValues[1], `is`(fakeFooSubtitles[1]))
        assertThat(inserted.allValues[2], `is`(fakeBarSubtitles[0]))
        assertThat(inserted.allValues[3], `is`(fakeBarSubtitles[1]))

        `when`(externalSubDao.get(foo)).thenReturn(fakeFooSubtitles)
        `when`(externalSubDao.get(bar)).thenReturn(fakeBarSubtitles)

        val fooSubtitles = externalSubRepository.getSubtitles(foo)
        val barSubtitles = externalSubRepository.getSubtitles(bar)
        verify(externalSubDao, times(2)).get(ArgumentMatchers.anyString())
        assertThat(fooSubtitles.size, `is`(0))
    }


    @Test fun saveTwoSubtitleForTwoMediaCreateTemporaryFilesForThem_GetShouldReturnTwoForEach() {
        val foo = "foo.mkv"
        val bar = "bar.mkv"

        val fakeFooSubtitles = (0 until 2).map {
            val file = temp.newFile("$foo.$it.srt")
            externalSubRepository.saveSubtitle(file.path, foo)
            TestUtil.createExternalSub(file.path, foo)
        }

        val fakeBarSubtitles = (0 until 2).map {
            val file = temp.newFile("$bar.$it.srt")
            externalSubRepository.saveSubtitle(file.path, bar)
            TestUtil.createExternalSub(file.path, bar)
        }

        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.`when`<Any>(Uri::class.java, "decode", anyString()).thenAnswer { it.arguments[0] as String }

        val inserted = argumentCaptor<ExternalSub>()
        verify(externalSubDao, times(4)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(4))
        assertThat(inserted.allValues[0], `is`(fakeFooSubtitles[0]))
        assertThat(inserted.allValues[1], `is`(fakeFooSubtitles[1]))
        assertThat(inserted.allValues[2], `is`(fakeBarSubtitles[0]))
        assertThat(inserted.allValues[3], `is`(fakeBarSubtitles[1]))

        `when`(externalSubDao.get(foo)).thenReturn(fakeFooSubtitles)
        `when`(externalSubDao.get(bar)).thenReturn(fakeBarSubtitles)

        val fooSubtitles = externalSubRepository.getSubtitles(foo)
        val barSubtitles = externalSubRepository.getSubtitles(bar)
        verify(externalSubDao, times(2)).get(ArgumentMatchers.anyString())
        assertThat(fooSubtitles.size, `is`(2))
        assertThat(barSubtitles.size, `is`(2))

        assertThat(fooSubtitles, hasItem(fakeFooSubtitles[0].uri))
        assertThat(fooSubtitles, hasItem(fakeFooSubtitles[1].uri))
        assertThat(barSubtitles, hasItem(fakeBarSubtitles[0].uri))
        assertThat(barSubtitles, hasItem(fakeBarSubtitles[1].uri))
    }

}
