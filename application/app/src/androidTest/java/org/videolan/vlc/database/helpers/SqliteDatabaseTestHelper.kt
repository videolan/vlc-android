/*******************************************************************************
 * SqliteDatabaseTestHelper
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
 */

package org.videolan.vlc.database.helpers

import android.net.Uri
import androidx.core.content.contentValuesOf

private val NETWORK_FAV_TABLE_NAME = "fav_table"
private val NETWORK_FAV_URI = "uri"
private val NETWORK_FAV_TITLE = "title"
private val NETWORK_FAV_ICON_URL = "icon_url"

private val EXTERNAL_SUBTITLES_TABLE_NAME = "external_subtitles_table"
private val EXTERNAL_SUBTITLES_MEDIA_NAME = "media_name"
private val EXTERNAL_SUBTITLES_URI = "uri"

private val SLAVES_TABLE_NAME = "SLAVES_table"
private val SLAVES_MEDIA_PATH = "slave_media_mrl"
private val SLAVES_TYPE = "slave_type"
private val SLAVES_PRIORITY = "slave_priority"
private val SLAVES_URI = "slave_uri"

fun createSlavesTable(helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.execSQL("CREATE TABLE IF NOT EXISTS $SLAVES_TABLE_NAME ( slave_media_mrl TEXT PRIMARY KEY NOT NULL, slave_type INTEGER NOT NULL, slave_priority INTEGER, slave_uri TEXT NOT NULL);")
    db.close()
}

fun createExternalSubsTable_V26(helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.execSQL("CREATE TABLE IF NOT EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, media_name TEXT NOT NULL);")
    db.close()
}

fun createNetworkFavsTable(helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.execSQL("CREATE TABLE IF NOT EXISTS $NETWORK_FAV_TABLE_NAME ( uri TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, icon_url TEXT);")
    db.close()
}

fun saveSlave(mediaPath: String, type: Int, priority: Int, uriString: String, helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.replace(SLAVES_TABLE_NAME, null,
            contentValuesOf(SLAVES_MEDIA_PATH to mediaPath, SLAVES_TYPE to type,
                    SLAVES_PRIORITY to priority, SLAVES_URI to uriString))
    db.close()
}

fun saveExSubtitle(path: String, mediaName: String, helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    if (path.isEmpty() || mediaName.isEmpty())
        return
    db.replace(EXTERNAL_SUBTITLES_TABLE_NAME, null,
            contentValuesOf(EXTERNAL_SUBTITLES_URI to path,
                    EXTERNAL_SUBTITLES_MEDIA_NAME to mediaName))
    db.close()
}

fun saveNetworkFavItem(uri: Uri, title: String, iconUrl: String?, helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.replace(NETWORK_FAV_TABLE_NAME, null,
            contentValuesOf(NETWORK_FAV_URI to uri.toString(),
                    NETWORK_FAV_TITLE to Uri.encode(title),
                    NETWORK_FAV_ICON_URL to Uri.encode(iconUrl)))
    db.close()
}

fun clearDatabase(helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.execSQL("DROP TABLE IF EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS $SLAVES_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS $NETWORK_FAV_TABLE_NAME")
    db.close()
}
