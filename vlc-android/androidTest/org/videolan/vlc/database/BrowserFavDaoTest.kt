/*******************************************************************************
 *  BrowserFavDaoTest.kt
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

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.arch.persistence.room.Room
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.util.Constants
import org.videolan.vlc.utilities.getValue


@RunWith(AndroidJUnit4::class)
class BrowserFavDaoTest {
    private lateinit var database: MediaDatabase
    private lateinit var browserFavDao: BrowserFavDao
    val netwrokFav1: BrowserFav = BrowserFav(Uri.parse("upnp://http://[fe80::61a1:a5a4:c66:bc5d]:2869/u"), Constants.TYPE_NETWORK_FAV, "Test1", null)
    val netwrokFav2: BrowserFav = BrowserFav(Uri.parse("upnp://http://[fe80::61a1:a5a4:c66:bc6d]:2869/u"), Constants.TYPE_NETWORK_FAV, "Test2", null)
    val localFav1: BrowserFav = BrowserFav(Uri.parse("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv"), Constants.TYPE_LOCAL_FAV, "Test3", null)

    @Before fun createDao() {
        val context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
        browserFavDao = database.browserFavDao()

        browserFavDao.insert(netwrokFav1)
        browserFavDao.insert(netwrokFav2)
        browserFavDao.insert(localFav1)
    }

    @After fun closeDb() {
        database.close()
    }

    @Test fun getAllBrowserFavs() {
        val browsersFavs = getValue(browserFavDao.getAll())
        assertThat(browsersFavs.size, equalTo(3))

        assertThat(browsersFavs, hasItem(netwrokFav1))
        assertThat(browsersFavs, hasItem(netwrokFav2))
        assertThat(browsersFavs, hasItem(localFav1))
    }

    @Test fun getAllNetworkFavs() {
        val networkFavs = getValue(browserFavDao.getAllNetwrokFavs())

        assertThat(networkFavs.size, equalTo(2))
        assertThat(networkFavs, hasItem(netwrokFav1))
        assertThat(networkFavs, hasItem(netwrokFav2))
    }


    @Test fun getAllLocalFavs() {
        val localFavs = getValue(browserFavDao.getAllLocalFavs())

        assertThat(localFavs.size, equalTo(1))
        assertThat(localFavs, hasItem(localFav1))
    }

    @Test fun getBrowserFav() {
        val browser = browserFavDao.get(netwrokFav1.uri)
        assertThat(browser.size, equalTo(1))
        assertThat(browser[0], equalTo(netwrokFav1))

    }

    @Test fun deleteBrowserFav() {
        browserFavDao.delete(netwrokFav1.uri)
        val browsers = getValue(browserFavDao.getAll())
        assertThat(browsers.size, equalTo(2))

        assertThat(browsers, not(hasItem(netwrokFav1)))
        assertThat(browsers, hasItem(netwrokFav2))

        browserFavDao.delete(netwrokFav2.uri)
        browserFavDao.delete(localFav1.uri)
        val browserFavsAfterDeleteAll = getValue(browserFavDao.getAll())
        assertThat(browserFavsAfterDeleteAll.size, equalTo(0))
    }
}