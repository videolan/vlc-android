/*******************************************************************************
 *  Migrations.kt
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
import android.arch.persistence.room.migration.Migration
import org.videolan.vlc.util.Constants

private const val DIR_TABLE_NAME = "directories_table"
private const val MEDIA_TABLE_NAME = "media_table"
private const val PLAYLIST_TABLE_NAME = "playlist_table"
private const val PLAYLIST_MEDIA_TABLE_NAME = "playlist_media_table"
private const val SEARCHHISTORY_TABLE_NAME = "searchhistory_table"
private const val MRL_TABLE_NAME = "mrl_table"
private const val HISTORY_TABLE_NAME = "history_table"

private const val EXTERNAL_SUBTITLES_TABLE_NAME = "external_subtitles_table"
private const val SLAVES_TABLE_NAME = "SLAVES_table"
private const val FAV_TABLE_NAME = "fav_table"

fun dropUnnecessaryTables(database: SupportSQLiteDatabase) {
    database.execSQL("DROP TABLE IF EXISTS $DIR_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $MEDIA_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $PLAYLIST_MEDIA_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $PLAYLIST_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $PLAYLIST_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $SEARCHHISTORY_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $MRL_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE_NAME;")
}

val migration_1_2 = object:Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {}
}

val migration_2_3 = object:Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {}
}

val migration_3_4 = object:Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {}
}


val migration_4_5 = object:Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_5_6 = object:Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_6_7 = object:Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_7_8 = object:Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_8_9 = object:Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_9_10 = object:Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_10_11 = object:Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_11_12 = object:Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_12_13 = object:Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
    }
}

val migration_13_14 = object:Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_14_15 = object:Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_15_16 = object:Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_16_17 = object:Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_17_18 = object:Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
    }
}

val migration_18_19 = object:Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_19_20 = object:Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_20_21 = object:Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_21_22 = object:Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_22_23 = object:Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) { }
}

val migration_23_24 = object:Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS $FAV_TABLE_NAME;")
        database.execSQL("CREATE TABLE IF NOT EXISTS $FAV_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, icon_url TEXT);")
    }
}

val migration_24_25 = object:Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, media_name TEXT NOT NULL);")
    }
}

val migration_25_26 = object:Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS $SLAVES_TABLE_NAME ( slave_media_mrl TEXT PRIMARY KEY NOT NULL, slave_type INTEGER NOT NULL, slave_priority INTEGER, slave_uri TEXT NOT NULL);")
    }
}

val migration_26_27 = object:Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        dropUnnecessaryTables(database)

        val SLAVES_TABLE_NAME_TEMP =  "${SLAVES_TABLE_NAME}_TEMP"
        database.execSQL("UPDATE $SLAVES_TABLE_NAME SET slave_priority=2 WHERE slave_priority IS NULL;")
        database.execSQL("CREATE TABLE IF NOT EXISTS $SLAVES_TABLE_NAME_TEMP ( slave_media_mrl TEXT PRIMARY KEY NOT NULL, slave_type INTEGER NOT NULL, slave_priority INTEGER NOT NULL, slave_uri TEXT NOT NULL);")
        database.execSQL("INSERT INTO $SLAVES_TABLE_NAME_TEMP(slave_media_mrl, slave_type, slave_priority, slave_uri) SELECT slave_media_mrl, slave_type, slave_priority, slave_uri FROM $SLAVES_TABLE_NAME")
        database.execSQL("DROP TABLE $SLAVES_TABLE_NAME")
        database.execSQL("ALTER TABLE $SLAVES_TABLE_NAME_TEMP RENAME TO $SLAVES_TABLE_NAME")

        // Add a type column and set its value to 0 (till this version all favs were network favs)
        database.execSQL("ALTER TABLE $FAV_TABLE_NAME ADD COLUMN type INTEGER NOT NULL DEFAULT 0;")
    }
}
