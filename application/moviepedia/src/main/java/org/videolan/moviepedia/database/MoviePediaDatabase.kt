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

package org.videolan.moviepedia.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.videolan.moviepedia.database.models.MediaImage
import org.videolan.moviepedia.database.models.MediaMetadata
import org.videolan.moviepedia.database.models.MediaPersonJoin
import org.videolan.moviepedia.database.models.Person
import org.videolan.tools.SingletonHolder

private const val DB_NAME = "moviepedia_database"

@Database(entities = [MediaMetadata::class, Person::class, MediaPersonJoin::class, MediaImage::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MoviePediaDatabase: RoomDatabase() {
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun personDao(): PersonDao
    abstract fun mediaPersonActorJoinDao(): MediaPersonJoinDao
    abstract fun mediaMedataDataFullDao(): MediaMetadataDataFullDao
    abstract fun mediaImageDao(): MediaImageDao

    companion object : SingletonHolder<MoviePediaDatabase, Context>({ buildDatabase(it.applicationContext) })
}

private fun buildDatabase(context: Context) = Room.databaseBuilder(
        context.applicationContext,
        MoviePediaDatabase::class.java, DB_NAME
).build()
