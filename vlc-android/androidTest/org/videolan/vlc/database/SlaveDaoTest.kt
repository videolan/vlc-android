/*******************************************************************************
 *  SlaveDaoTest.kt
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

package org.videolan.vlc.database

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.Media
import org.videolan.vlc.database.models.Slave


@RunWith(AndroidJUnit4::class)
class SlaveDaoTest {
    private lateinit var database: MediaDatabase
    private lateinit var slaveDao: SlaveDao
    val media1Path = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv"
    val media1UriEng = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.eng.srt"
    val media1UriFa = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.fa.srt"

    val media2Path = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file2.mkv"
    val media2UriEng = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file2.eng.srt"

    private val slave1eng = Slave(media1Path, Media.Slave.Type.Subtitle, 0, media1UriEng)
    private val slave1fa = Slave(media1Path, Media.Slave.Type.Subtitle, 0, media1UriFa)
    private val slave2 = Slave(media2Path, Media.Slave.Type.Subtitle, 1, media2UriEng )

    @Before fun createDao() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        slaveDao = database.slaveDao()

        slaveDao.insert(slave1eng)
        slaveDao.insert(slave1fa)
        slaveDao.insert(slave2)
    }

    @After fun closeDb() {
        database.close()
    }

    @Test fun getSlaves() {
        val s1 = slaveDao.get(media1Path)
        assertThat(s1.size, equalTo(1))
        assertThat(s1, not(hasItem(slave1eng)))
        assertThat(s1, hasItem(slave1fa))

        val s2 = slaveDao.get(media2Path)
        assertThat(s2.size, equalTo(1))
        assertThat(s2, hasItem(slave2))
    }
}
