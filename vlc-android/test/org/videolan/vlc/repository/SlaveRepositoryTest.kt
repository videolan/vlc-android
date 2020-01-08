/*******************************************************************************
 *  SlaveRepositoryTest.kt
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
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.SlaveDao
import org.videolan.vlc.mediadb.models.Slave
import org.videolan.vlc.util.TestUtil
import org.videolan.vlc.util.argumentCaptor
import org.videolan.vlc.util.mock
import org.videolan.vlc.util.uninitialized

@RunWith(PowerMockRunner::class)
@PrepareForTest(Uri::class)
class SlaveRepositoryTest {
    private val slaveDao = mock<SlaveDao>()
    private lateinit var slaveRepository: SlaveRepository

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before fun init() {
        val db = mock<MediaDatabase>()
        `when`(db.slaveDao()).thenReturn(slaveDao)
        slaveRepository = SlaveRepository(slaveDao)
    }


    @Test fun saveOneSlave_getSlaveShouldReturnOne() = runBlocking {
        val fakeSlave = TestUtil.createSubtitleSlavesForMedia("foo.mkv", 1)[0]
        slaveRepository.saveSlave(fakeSlave.mediaPath, fakeSlave.type, fakeSlave.priority, fakeSlave.uri)

        val inserted = argumentCaptor<org.videolan.vlc.mediadb.models.Slave>()
        verify(slaveDao).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.value, `is`(fakeSlave))

        PowerMockito.mockStatic(Uri::class.java)
        PowerMockito.`when`<Any>(Uri::class.java, "decode", anyString()).thenAnswer { it.arguments[0] as String }

        `when`(slaveDao.get(fakeSlave.mediaPath)).thenReturn(listOf(fakeSlave))

        val slave = slaveRepository.getSlaves(fakeSlave.mediaPath)[0]
        assertThat(slave.uri, `is`(fakeSlave.uri))
        assertThat(slave.type, `is`(fakeSlave.type))
        assertThat(slave.priority, `is`(fakeSlave.priority))
    }
}
