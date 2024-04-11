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

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.TYPE_LOCAL_FAV
import org.videolan.tools.Settings

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
private const val WIDGET_TABLE_NAME = "widget_table"
private const val CUSTOM_DIRECTORY_TABLE_NAME = "CustomDirectory"

fun dropUnnecessaryTables(database: SupportSQLiteDatabase) {
    database.execSQL("DROP TABLE IF EXISTS $DIR_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $MEDIA_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $PLAYLIST_MEDIA_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $PLAYLIST_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $SEARCHHISTORY_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $MRL_TABLE_NAME;")
    database.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE_NAME;")
}

val migration_1_2 = object:Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val migration_2_3 = object:Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val migration_3_4 = object:Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}


val migration_4_5 = object:Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_5_6 = object:Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_6_7 = object:Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_7_8 = object:Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_8_9 = object:Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_9_10 = object:Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_10_11 = object:Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_11_12 = object:Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_12_13 = object:Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val migration_13_14 = object:Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_14_15 = object:Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_15_16 = object:Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_16_17 = object:Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_17_18 = object:Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val migration_18_19 = object:Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_19_20 = object:Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_20_21 = object:Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_21_22 = object:Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_22_23 = object:Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) { }
}

val migration_23_24 = object:Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS $FAV_TABLE_NAME;")
        db.execSQL("CREATE TABLE IF NOT EXISTS $FAV_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, icon_url TEXT);")
    }
}

val migration_24_25 = object:Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, media_name TEXT NOT NULL);")
    }
}

val migration_25_26 = object:Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS $SLAVES_TABLE_NAME ( slave_media_mrl TEXT PRIMARY KEY NOT NULL, slave_type INTEGER NOT NULL, slave_priority INTEGER, slave_uri TEXT NOT NULL);")
    }
}

val migration_26_27 = object:Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dropUnnecessaryTables(db)

        val slavesTableNameTemp =  "${SLAVES_TABLE_NAME}_TEMP"
        db.execSQL("UPDATE $SLAVES_TABLE_NAME SET slave_priority=2 WHERE slave_priority IS NULL;")
        db.execSQL("CREATE TABLE IF NOT EXISTS $slavesTableNameTemp ( slave_media_mrl TEXT PRIMARY KEY NOT NULL, slave_type INTEGER NOT NULL, slave_priority INTEGER NOT NULL, slave_uri TEXT NOT NULL);")
        db.execSQL("INSERT INTO $slavesTableNameTemp(slave_media_mrl, slave_type, slave_priority, slave_uri) SELECT slave_media_mrl, slave_type, slave_priority, slave_uri FROM $SLAVES_TABLE_NAME")
        db.execSQL("DROP TABLE $SLAVES_TABLE_NAME")
        db.execSQL("ALTER TABLE $slavesTableNameTemp RENAME TO $SLAVES_TABLE_NAME")

        // Add a type column and set its value to 0 (till this version all favs were network favs)
        db.execSQL("ALTER TABLE $FAV_TABLE_NAME ADD COLUMN type INTEGER NOT NULL DEFAULT 0;")
    }
}

val migration_27_28 = object:Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val preferences = Settings.getInstance(AppContextProvider.appContext)
        val customPaths = preferences.getString("custom_paths", "")
        var oldPaths : List<String>? = null
        if (!customPaths.isNullOrEmpty()) oldPaths = customPaths.split(":")

        db.execSQL("CREATE TABLE IF NOT EXISTS $CUSTOM_DIRECTORY_TABLE_NAME(path TEXT PRIMARY KEY NOT NULL);")
        oldPaths?.forEach {
            db.execSQL("INSERT INTO $CUSTOM_DIRECTORY_TABLE_NAME(path) VALUES (\"$it\")")
        }
    }
}

val migration_28_29 = object:Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop old External Subtitle Table
        db.execSQL("DROP TABLE IF EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME;")
        db.execSQL("CREATE TABLE IF NOT EXISTS `${EXTERNAL_SUBTITLES_TABLE_NAME}` (`idSubtitle` TEXT NOT NULL, `subtitlePath` TEXT NOT NULL, `mediaPath` TEXT NOT NULL, `subLanguageID` TEXT NOT NULL, `movieReleaseName` TEXT NOT NULL, PRIMARY KEY(`mediaPath`, `idSubtitle`))")
    }
}

val migration_29_30 = object:Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Insert the new whatsapp path
        val uri = AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI_A11
        db.execSQL("INSERT INTO  $FAV_TABLE_NAME(uri, type, title, icon_url) VALUES (\"$uri\", 1, \"${uri.lastPathSegment}\", null)")
    }
}

val migration_30_31 = object:Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `${WIDGET_TABLE_NAME}` ( `id` INTEGER PRIMARY KEY NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `theme` INTEGER NOT NULL, `light_theme` INTEGER NOT NULL, `background_color` INTEGER NOT NULL, `foreground_color` INTEGER NOT NULL, `forward_delay` INTEGER NOT NULL, `rewind_delay` INTEGER NOT NULL, `opacity` INTEGER NOT NULL, `show_configure` INTEGER NOT NULL);")
    }
}

val migration_31_32 = object:Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $WIDGET_TABLE_NAME ADD COLUMN show_seek INTEGER NOT NULL DEFAULT 0;")
    }
}

val migration_32_33 = object:Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $WIDGET_TABLE_NAME ADD COLUMN show_cover INTEGER NOT NULL DEFAULT 0;")
    }
}

val migration_33_34 = object:Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $WIDGET_TABLE_NAME ADD COLUMN type INTEGER NOT NULL DEFAULT 0;")
    }
}

val migration_34_35 = object:Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val slavesTableNameTemp =  "${SLAVES_TABLE_NAME}_TEMP"
        db.execSQL("CREATE TABLE IF NOT EXISTS $slavesTableNameTemp (" +
                "slave_media_mrl TEXT NOT NULL, " +
                "slave_type INTEGER NOT NULL, " +
                "slave_priority INTEGER NOT NULL, " +
                "slave_uri TEXT NOT NULL, " +
                "PRIMARY KEY (slave_media_mrl, slave_uri));")

        db.execSQL("INSERT INTO $slavesTableNameTemp(slave_media_mrl, slave_type, slave_priority, slave_uri) SELECT slave_media_mrl, slave_type, slave_priority, slave_uri FROM $SLAVES_TABLE_NAME")

        db.execSQL("DROP TABLE $SLAVES_TABLE_NAME")
        db.execSQL("ALTER TABLE $slavesTableNameTemp RENAME TO $SLAVES_TABLE_NAME")

    }
}

@OptIn(DelicateCoroutinesApi::class)
fun populateDB(context: Context) = GlobalScope.launch(Dispatchers.IO) {
    val uris = listOf(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI,
            AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI,
            AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI,
            AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI,
            AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI,
            AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI_A11)
    val browserFavDao = MediaDatabase.getInstance(context).browserFavDao()

    for (uri in uris) browserFavDao.insert(org.videolan.vlc.mediadb.models.BrowserFav(uri, TYPE_LOCAL_FAV, uri.lastPathSegment
            ?: "", null))
}