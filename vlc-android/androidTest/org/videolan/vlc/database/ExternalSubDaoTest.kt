/*******************************************************************************
 *  ExternalSubDaoTest.kt
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
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.database.models.ExternalSub

@RunWith(AndroidJUnit4::class)
class ExternalSubDaoTest {
    private lateinit var database: MediaDatabase
    private lateinit var externalSubDao: ExternalSubDao
    private val file1 = "file1.mkv"
    private val file2 = "file2.mkv"
    private val file1Sub1 = ExternalSub("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.eng.srt", file1)
    private val file1Sub2 = ExternalSub("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.fa.srt", file1)
    private val file1Sub3 = ExternalSub("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.fr.srt", file1)
    private val file2Sub1 = ExternalSub("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file2.eng.srt", file2)

    @Before fun createDao() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        externalSubDao = database.externalSubDao()

        externalSubDao.insert(file1Sub1)
        externalSubDao.insert(file1Sub2)
        externalSubDao.insert(file1Sub3)
        externalSubDao.insert(file2Sub1)
    }

    @After fun closeDb() {
        database.close()
    }

    @Test fun getExternalSubtitles() {
        val file1Subtitles = externalSubDao.get(file1)
        assertThat(file1Subtitles.size, equalTo(3))
        assertThat(file1Subtitles, hasItem(file1Sub1))
        assertThat(file1Subtitles, hasItem(file1Sub2))
        assertThat(file1Subtitles, hasItem(file1Sub3))

        val file2Subtitles = externalSubDao.get(file2)
        assertThat(file2Subtitles.size, equalTo(1))
        assertThat(file2Subtitles, hasItem(file2Sub1))
    }

    @Test fun deleteSubtitle(){
        externalSubDao.delete(file1Sub1)
        var file1Subtitles = externalSubDao.get(file1)
        assertThat(file1Subtitles, not(hasItem(file1Sub1)))
        assertThat(file1Subtitles, hasItem(file1Sub2))
        assertThat(file1Subtitles, hasItem(file1Sub3))

        externalSubDao.delete(file1Sub2)
        file1Subtitles = externalSubDao.get(file1)
        assertThat(file1Subtitles, not(hasItem(file2)))
        assertThat(file1Subtitles, hasItem(file1Sub3))

        externalSubDao.delete(file1Sub3)
        file1Subtitles = externalSubDao.get(file1)
        assertThat(file1Subtitles, not(hasItem(file1Sub3)))
        assertThat(file1Subtitles, empty())

        externalSubDao.delete(file2Sub1)
        val file2Subtitles = externalSubDao.get(file2)
        assertThat(file2Subtitles, empty())
    }

}