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

import android.content.ContentValues
import android.net.Uri
import android.text.TextUtils

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
    val values = ContentValues()
    values.put(SLAVES_MEDIA_PATH, mediaPath)
    values.put(SLAVES_TYPE, type)
    values.put(SLAVES_PRIORITY, priority)
    values.put(SLAVES_URI, uriString)
    db.replace(SLAVES_TABLE_NAME, null, values)
    db.close()
}

fun saveExSubtitle(path: String, mediaName: String, helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mediaName))
        return
    val values = ContentValues()
    values.put(EXTERNAL_SUBTITLES_URI, path)
    values.put(EXTERNAL_SUBTITLES_MEDIA_NAME, mediaName)
    db.replace(EXTERNAL_SUBTITLES_TABLE_NAME, null, values)
    db.close()
}

fun saveNetworkFavItem(uri: Uri, title: String, iconUrl: String?, helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    val values = ContentValues()
    values.put(NETWORK_FAV_URI, uri.toString())
    values.put(NETWORK_FAV_TITLE, Uri.encode(title))
    values.put(NETWORK_FAV_ICON_URL, Uri.encode(iconUrl))
    db.replace(NETWORK_FAV_TABLE_NAME, null, values)
    db.close()
}

fun clearDatabase(helper: SqliteTestDbOpenHelper) {
    val db = helper.writableDatabase
    db.execSQL("DROP TABLE IF EXISTS $EXTERNAL_SUBTITLES_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS $SLAVES_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS $NETWORK_FAV_TABLE_NAME")
    db.close()
}
