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

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.database.MediaDatabase
import java.io.File


@RunWith(AndroidJUnit4::class)
class ExternalSubRepositoryTest {
    private lateinit var database: MediaDatabase
    private lateinit var externalSubRepository: ExternalSubRepository
    private val media1Name = "file1.mkv"
    private val subsFolder = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/"
    private val file1Sub1 = "${subsFolder}file1.eng.srt"
    private val file1Sub2 = "${subsFolder}file1.fa.srt"
    private val file1Sub3 = "${subsFolder}file1.fr.srt"

    @Before
    fun onCreateRepository() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        externalSubRepository = ExternalSubRepository(context, mediaDatabase = database)

        val subsFolder = File(subsFolder)
        subsFolder.mkdirs()
    }

    @After fun clean() {
        val subsFolder = File(subsFolder)
        subsFolder.deleteRecursively()
    }

    @Test fun saveSubtitle() = runBlocking{
        externalSubRepository.saveSubtitle(file1Sub1, media1Name)
        externalSubRepository.saveSubtitle(file1Sub2, media1Name)
        externalSubRepository.saveSubtitle(file1Sub3, media1Name)

        val file1 = File(file1Sub1)
        file1.createNewFile()
        val file2 = File(file1Sub2)
        file2.createNewFile()
        val file3 = File(file1Sub3)
        file3.createNewFile()

        val externalSubs = externalSubRepository.getSubtitles(media1Name)
        assertThat(externalSubs.size, equalTo(3))

        file1.delete()
        var externalSubsAfterDelete = externalSubRepository.getSubtitles(media1Name)
        assertThat(externalSubsAfterDelete.size, equalTo(2))

        file2.delete()
        externalSubsAfterDelete = externalSubRepository.getSubtitles(media1Name)
        assertThat(externalSubsAfterDelete.size, equalTo(1))

        file3.delete()
        externalSubsAfterDelete = externalSubRepository.getSubtitles(media1Name)
        assertThat(externalSubsAfterDelete.size, equalTo(0))

    }



}