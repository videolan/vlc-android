/*******************************************************************************
 *  MediaDatabase.kt
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

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.content.Context
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.database.models.CustomDirectory
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.database.models.Slave

private const val DB_NAME = "vlc_database"

@Database(entities = [ExternalSub::class, Slave::class, BrowserFav::class, CustomDirectory::class], version = 28)
@TypeConverters(Converters::class)
abstract class MediaDatabase: RoomDatabase() {
    abstract fun externalSubDao(): ExternalSubDao
    abstract fun slaveDao(): SlaveDao
    abstract fun browserFavDao(): BrowserFavDao
    abstract fun customDirectoryDao(): CustomDirectoryDao

    companion object : SingletonHolder<MediaDatabase, Context>({ buildDatabase(it.applicationContext) })
}

private fun buildDatabase(context: Context) = Room.databaseBuilder(context.applicationContext,
        MediaDatabase::class.java, DB_NAME)
        .addMigrations(migration_1_2, migration_2_3, migration_3_4, migration_4_5,
                migration_5_6, migration_6_7, migration_7_8, migration_8_9,
                migration_9_10, migration_10_11, migration_11_12, migration_12_13,
                migration_13_14, migration_14_15, migration_15_16, migration_16_17,
                migration_17_18, migration_18_19, migration_19_20, migration_20_21,
                migration_21_22, migration_22_23, migration_23_24, migration_24_25,
                migration_25_26, migration_26_27, migration_27_28)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { populateDB(context) }
        })
        .build()

