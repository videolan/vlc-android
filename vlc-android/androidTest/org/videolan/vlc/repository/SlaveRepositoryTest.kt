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

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.Media
import org.videolan.vlc.database.MediaDatabase


@RunWith(AndroidJUnit4::class)
class SlaveRepositoryTest {
    private lateinit var database: MediaDatabase
    private lateinit var slaveRepository: SlaveRepository
    val media1Path = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv"
    val media1UriEng = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.eng.srt"
    val media1UriFa = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.fa.srt"

    val media2Path = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file2.mkv"
    val media2UriEng = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file2.eng.srt"

    @Before fun onCreateRepository() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        slaveRepository = SlaveRepository(context, mediaDatabase = database)
    }

    @After fun closeDb() {
        database.close()
    }

    @Test fun saveSlave() = runBlocking {
        val slavesBeforeInsert = slaveRepository.getSlaves(media1Path)
        assertThat(slavesBeforeInsert.size, equalTo(0))
        slaveRepository.saveSlave(media1Path, Media.Slave.Type.Subtitle, 2, media1UriEng)
        slaveRepository.saveSlave(media1Path, Media.Slave.Type.Subtitle, 2, media1UriFa)

        val slavesAfterInsert = slaveRepository.getSlaves(media1Path)
        assertThat(slavesAfterInsert.size, equalTo(1))
    }

    @Test fun getSlaves() = runBlocking{
        slaveRepository.saveSlave(media1Path, Media.Slave.Type.Subtitle, 2, media1UriEng).join()
        val correctMediSlave = Media.Slave(Media.Slave.Type.Subtitle, 2, media1UriEng)
        val slaves = slaveRepository.getSlaves(media1Path)
        assertEquals(slaves[0].uri, correctMediSlave.uri)
        assertEquals(slaves[0].type, correctMediSlave.type)
        assertEquals(slaves[0].priority, correctMediSlave.priority)
    }
}