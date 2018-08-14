/*******************************************************************************
 *  MigrationTest.kt
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

import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory
import android.arch.persistence.room.Room
import android.arch.persistence.room.migration.Migration
import android.arch.persistence.room.testing.MigrationTestHelper
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.Media
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.database.helpers.*
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.database.models.Slave
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.TYPE_NETWORK_FAV
import org.videolan.vlc.util.TestUtil

private const val TEST_DB_NAME = "test-db"

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate26To27() {
        // Slave
        val slaveMedia1Path = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv"
        val slaveMedia1UriFa = "file:///storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.fa.srt"
        // External Sub
        val exSubMedia1Name = "file1.mkv"
        val exSubMedisubsFolder = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/"
        val exSubfile1Sub1 = "${exSubMedisubsFolder}file1.eng.srt"
        // Favs
        val favUri = Uri.parse("/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/file1.mkv")
        val favTitle = "test1"

        val sqliteTestDbOpenHelper = SqliteTestDbOpenHelper(InstrumentationRegistry.getTargetContext(), TEST_DB_NAME, 26)
        createSlavesTable(sqliteTestDbOpenHelper)
        createExternalSubsTable(sqliteTestDbOpenHelper)
        createNetworkFavsTable(sqliteTestDbOpenHelper)

        // Insert Data before migration
        saveSlave(slaveMedia1Path, Media.Slave.Type.Subtitle, 2, slaveMedia1UriFa, sqliteTestDbOpenHelper)
        saveExSubtitle(exSubfile1Sub1, exSubMedia1Name, sqliteTestDbOpenHelper)
        saveNetworkFavItem(favUri, favTitle, null, sqliteTestDbOpenHelper)

        migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 27, true, migration_26_27, migration_27_28)

        val migratedDb = getMigratedRoomDatabase(migration_26_27, migration_27_28)

        val slave: Slave = migratedDb.slaveDao().get(slaveMedia1Path)[0]
        val exSub: ExternalSub = migratedDb.externalSubDao().get(exSubMedia1Name)[0]
        val fav: BrowserFav = migratedDb.browserFavDao().get(favUri)[0]

        assertEquals(slave.mediaPath, slaveMedia1Path)
        assertEquals(slave.type, Media.Slave.Type.Subtitle)
        assertEquals(slave.priority, 2)
        assertEquals(slave.uri, slaveMedia1UriFa)

        assertEquals(exSub.uri, exSubfile1Sub1)
        assertEquals(exSub.mediaName, exSubMedia1Name)

        assertEquals(fav.uri, favUri)
        assertEquals(fav.title, favTitle)
        assertEquals(fav.iconUrl, null)
        assertEquals(fav.type, TYPE_NETWORK_FAV)

        clearDatabase(sqliteTestDbOpenHelper)
    }

    @Test fun migrate27to28() {
        migrationTestHelper.createDatabase(TEST_DB_NAME, 27)
        val preferences = Settings.getInstance(VLCApplication.getAppContext()).edit()
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        // 27_28 migration rule moves the data from prefs to room
        val prefCustomDirectories = fakeCustomDirectories.map { it.path }.reduce{ acc, path -> "$acc:$path" }
        preferences.putString("custom_paths", prefCustomDirectories ).commit()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 28, true, migration_27_28)
        val roomCustomDirectories = getMigratedRoomDatabase(migration_27_28).customDirectoryDao().getAll()
        assertThat(roomCustomDirectories.size, `is`(2))
        assertThat(roomCustomDirectories, hasItem(fakeCustomDirectories[0]))
        assertThat(roomCustomDirectories, hasItem(fakeCustomDirectories[1]))
    }

    fun getMigratedRoomDatabase(vararg migrations:Migration): MediaDatabase {
        val database: MediaDatabase = Room.databaseBuilder(
                InstrumentationRegistry.getTargetContext(),
                MediaDatabase::class.java, TEST_DB_NAME )
                .addMigrations(*migrations)
                .build()

        // close the database and release any stream resources when the test finishes
        migrationTestHelper.closeWhenFinished(database);
        return database
    }
}