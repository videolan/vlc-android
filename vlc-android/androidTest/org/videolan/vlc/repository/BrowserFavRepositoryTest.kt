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

import android.arch.persistence.room.Room
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.Assert.assertNull
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.utilities.getValue


@RunWith(AndroidJUnit4::class)
class BrowserFavRepositoryTest {
    private lateinit var database: MediaDatabase
    private lateinit var browserFavRepository: BrowserFavRepository
    private val uri = Uri.parse("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv")

    @Before
    fun onCreateRepository() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        browserFavRepository = BrowserFavRepository(context, mediaDatabase = database)
    }


    @Test fun addBrowserFaveItem() {
        val title = "test1"
        runBlocking {
            browserFavRepository.addLocalFavItem(uri, title, null).join()
            val exists = browserFavRepository.browserFavExists(uri)
            assertThat(exists, equalTo(true))
        }

        val browserFavs = getValue(browserFavRepository.getAllBrowserFavs())
        assertThat(browserFavs[0].uri, equalTo(uri))
        assertThat(browserFavs[0].title, equalTo(title))
        assertNull(browserFavs[0].artworkURL)
    }

    @Test fun deleteBrowserFav() = runBlocking {
        val title = "test1"
        browserFavRepository.addLocalFavItem(uri, title, null).join()
        browserFavRepository.deleteBrowserFav(uri)
        val favs = getValue(browserFavRepository.getAllBrowserFavs())
        assertThat(favs.size, equalTo(0))
    }
}