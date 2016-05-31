/*****************************************************************************
 * MediaDatabase.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.media;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.VLCApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class MediaDatabase {
    public final static String TAG = "VLC/MediaDatabase";

    private static MediaDatabase instance;

    private SQLiteDatabase mDb;
    private static final String DB_NAME = "vlc_database";
    private static final int DB_VERSION = 26;
    private static final int CHUNK_SIZE = 50;

    private static final String DIR_TABLE_NAME = "directories_table";
    private static final String DIR_ROW_PATH = "path";

    private static final String MEDIA_TABLE_NAME = "media_table";
    private static final String MEDIA_VIRTUAL_TABLE_NAME = "media_table_fts";
    public static final String MEDIA_LOCATION = "_id"; //standard key for primary key, needed for search suggestions
    private static final String MEDIA_TIME = "time";
    private static final String MEDIA_LENGTH = "length";
    private static final String MEDIA_TYPE = "type";
    private static final String MEDIA_PICTURE = "picture";
    public static final String MEDIA_TITLE = "title";
    private static final String MEDIA_ARTIST = "artist";
    private static final String MEDIA_GENRE = "genre";
    private static final String MEDIA_ALBUM = "album";
    private static final String MEDIA_ALBUMARTIST = "albumartist";
    private static final String MEDIA_WIDTH = "width";
    private static final String MEDIA_HEIGHT = "height";
    private static final String MEDIA_ARTWORKURL = "artwork_url";
    private static final String MEDIA_AUDIOTRACK = "audio_track";
    private static final String MEDIA_SPUTRACK = "spu_track";
    private static final String MEDIA_TRACKNUMBER = "track_number";
    private static final String MEDIA_DISCNUMBER = "disc_number";
    private static final String MEDIA_LAST_MODIFIED = "last_modified";

    private static final String PLAYLIST_TABLE_NAME = "playlist_table";
    private static final String PLAYLIST_NAME = "name";

    private static final String PLAYLIST_MEDIA_TABLE_NAME = "playlist_media_table";
    private static final String PLAYLIST_MEDIA_ID = "id";
    private static final String PLAYLIST_MEDIA_PLAYLISTNAME = "playlist_name";
    private static final String PLAYLIST_MEDIA_MEDIALOCATION = "media_location";
    private static final String PLAYLIST_MEDIA_ORDER = "playlist_order";

    private static final String SEARCHHISTORY_TABLE_NAME = "searchhistory_table";
    private static final String SEARCHHISTORY_DATE = "date";
    private static final String SEARCHHISTORY_KEY = "key";

    private static final String MRL_TABLE_NAME = "mrl_table";
    private static final String MRL_DATE = "date";
    private static final String MRL_URI = "uri";
    private static final String MRL_TABLE_SIZE = "100";

    private static final String EXTERNAL_SUBTITLES_TABLE_NAME = "external_subtitles_table";
    private static final String EXTERNAL_SUBTITLES_MEDIA_NAME = "media_name";
    private static final String EXTERNAL_SUBTITLES_URI = "uri";

    private static final String SLAVES_TABLE_NAME = "SLAVES_table";
    private static final String SLAVES_MEDIA_PATH = "slave_media_mrl";
    private static final String SLAVES_TYPE = "slave_type";
    private static final String SLAVES_PRIORITY = "slave_priority";
    private static final String SLAVES_URI = "slave_uri";

    private static final String HISTORY_TABLE_NAME = "history_table";
    private static final String HISTORY_DATE = MEDIA_LAST_MODIFIED;
    private static final String HISTORY_TITLE = MEDIA_TITLE;
    private static final String HISTORY_ARTIST = MEDIA_ARTIST;
    private static final String HISTORY_URI = MEDIA_LOCATION;
    private static final String HISTORY_TYPE = MEDIA_TYPE;
    private static final String HISTORY_TABLE_SIZE = "100";

    private static final String NETWORK_FAV_TABLE_NAME = "fav_table";
    private static final String NETWORK_FAV_URI = "uri";
    private static final String NETWORK_FAV_TITLE = "title";
    private static final String NETWORK_FAV_ICON_URL = "icon_url";

    //    public static final int INDEX_MEDIA_TABLE_NAME = 0;
//    public static final int INDEX_MEDIA_PATH = 1;
    public static final int INDEX_MEDIA_TIME = 2;
    public static final int INDEX_MEDIA_LENGTH = 3;
    //    public static final int INDEX_MEDIA_TYPE = 4;
    public static final int INDEX_MEDIA_PICTURE = 5;
    //    public static final int INDEX_MEDIA_TITLE = 6;
//    public static final int INDEX_MEDIA_ARTIST = 7;
//    public static final int INDEX_MEDIA_GENRE = 8;
//    public static final int INDEX_MEDIA_ALBUM = 9;
//    public static final int INDEX_MEDIA_ALBUMARTIST = 10;
//    public static final int INDEX_MEDIA_WIDTH = 11;
//    public static final int INDEX_MEDIA_HEIGHT = 12;
//    public static final int INDEX_MEDIA_ARTWORKURL = 13;
    public static final int INDEX_MEDIA_AUDIOTRACK = 14;
    public static final int INDEX_MEDIA_SPUTRACK = 15;
//    public static final int INDEX_MEDIA_TRACKNUMBER = 16;
//    public static final int INDEX_MEDIA_DISCNUMBER = 17;
//    public static final int INDEX_MEDIA_LAST_MODIFIED = 18;

    /**
     * Constructor
     *
     * @param context
     */
    private MediaDatabase(Context context) {
        // create or open database
        DatabaseHelper helper = new DatabaseHelper(context);
        this.mDb = helper.getWritableDatabase();
    }

    public synchronized static MediaDatabase getInstance() {
        if (instance == null) {
            instance = new MediaDatabase(VLCApplication.getAppContext());
        }
        return instance;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public SQLiteDatabase getWritableDatabase() {
            SQLiteDatabase db;
            try {
                return super.getWritableDatabase();
            } catch(SQLiteException e) {
                try {
                    db = SQLiteDatabase.openOrCreateDatabase(VLCApplication.getAppContext().getDatabasePath(DB_NAME), null);
                } catch(SQLiteException e2) {
                    Log.w(TAG, "SQLite database could not be created! Media library cannot be saved.");
                    db = SQLiteDatabase.create(null);
                }
            }
            int version = db.getVersion();
            if (version != DB_VERSION) {
                db.beginTransaction();
                try {
                    if (version == 0) {
                        onCreate(db);
                    } else {
                        onUpgrade(db, version, DB_VERSION);
                    }
                    db.setVersion(DB_VERSION);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            return db;
        }

        public void dropMediaTableQuery(SQLiteDatabase db) {
            try {
                String query = "DROP TABLE " + MEDIA_TABLE_NAME + ";";
                db.execSQL(query);
                query = "DROP TABLE " + MEDIA_VIRTUAL_TABLE_NAME + ";";
                db.execSQL(query);
            } catch(SQLiteException e)
            {
                Log.w(TAG, "SQLite tables could not be dropped! Maybe they were missing...");
            }
        }

        public void createMediaTableQuery(SQLiteDatabase db) {
            String query = "CREATE TABLE IF NOT EXISTS "
                    + MEDIA_TABLE_NAME + " ("
                    + MEDIA_LOCATION + " TEXT PRIMARY KEY NOT NULL, "
                    + MEDIA_TIME + " INTEGER, "
                    + MEDIA_LENGTH + " INTEGER, "
                    + MEDIA_TYPE + " INTEGER, "
                    + MEDIA_PICTURE + " BLOB, "
                    + MEDIA_TITLE + " TEXT, "
                    + MEDIA_ARTIST + " TEXT, "
                    + MEDIA_GENRE + " TEXT, "
                    + MEDIA_ALBUM + " TEXT, "
                    + MEDIA_ALBUMARTIST + " TEXT, "
                    + MEDIA_WIDTH + " INTEGER, "
                    + MEDIA_HEIGHT + " INTEGER, "
                    + MEDIA_ARTWORKURL + " TEXT, "
                    + MEDIA_AUDIOTRACK + " INTEGER, "
                    + MEDIA_SPUTRACK + " INTEGER, "
                    + MEDIA_TRACKNUMBER + " INTEGER, "
                    + MEDIA_DISCNUMBER + " INTEGER, "
                    + MEDIA_LAST_MODIFIED + " INTEGER"
                    + ");";
            db.execSQL(query);
            db.execSQL("PRAGMA recursive_triggers='ON'"); //Needed for delete trigger
            query = "CREATE VIRTUAL TABLE "
                    + MEDIA_VIRTUAL_TABLE_NAME + " USING FTS3 ("
                    + MEDIA_LOCATION + ", "
                    + MEDIA_TITLE + ", "
                    + MEDIA_ARTIST + ", "
                    + MEDIA_GENRE + ", "
                    + MEDIA_ALBUM + ", "
                    + MEDIA_ALBUMARTIST
                    + ");";
            db.execSQL(query);
            query = " CREATE TRIGGER media_insert_trigger AFTER INSERT ON "+
                    MEDIA_TABLE_NAME+ " BEGIN "+
                    "INSERT INTO "+MEDIA_VIRTUAL_TABLE_NAME+" ("+MEDIA_LOCATION+", "+MEDIA_TITLE+
                    ", "+MEDIA_ARTIST+", "+MEDIA_GENRE+", "+MEDIA_ALBUM+", "+MEDIA_ALBUMARTIST+" )"+
                    " VALUES (new."+MEDIA_LOCATION+", new."+MEDIA_TITLE+", new."+MEDIA_ARTIST+
                    ", new."+MEDIA_GENRE+", new."+MEDIA_ALBUM+", new."+MEDIA_ALBUMARTIST+
                    "); END;";
            db.execSQL(query);
            query = " CREATE TRIGGER media_delete_trigger AFTER DELETE ON "+MEDIA_TABLE_NAME+ " BEGIN "+
                    "DELETE FROM "+MEDIA_VIRTUAL_TABLE_NAME+" WHERE "+MEDIA_LOCATION+" = old."+MEDIA_LOCATION+";"+
                    " END;";
            db.execSQL(query);
        }

        private void createPlaylistTablesQuery(SQLiteDatabase db) {
            String createPlaylistTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    PLAYLIST_TABLE_NAME + " (" +
                    PLAYLIST_NAME + " VARCHAR(200) PRIMARY KEY NOT NULL);";

            db.execSQL(createPlaylistTableQuery);

            String createPlaylistMediaTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    PLAYLIST_MEDIA_TABLE_NAME + " (" +
                    PLAYLIST_MEDIA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    PLAYLIST_MEDIA_PLAYLISTNAME + " VARCHAR(200) NOT NULL," +
                    PLAYLIST_MEDIA_MEDIALOCATION + " TEXT NOT NULL," +
                    PLAYLIST_MEDIA_ORDER + " INTEGER NOT NULL);";

            db.execSQL(createPlaylistMediaTableQuery);
        }

        private void createMRLTableQuery(SQLiteDatabase db) {
            String createMrlTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    MRL_TABLE_NAME + " (" +
                    MRL_URI + " TEXT PRIMARY KEY NOT NULL,"+
                    MRL_DATE + " DATETIME NOT NULL"
                    +");";
            db.execSQL(createMrlTableQuery);
            createMrlTableQuery = " CREATE TRIGGER mrl_history_trigger AFTER INSERT ON "+
                    MRL_TABLE_NAME+ " BEGIN "+
                    " DELETE FROM "+MRL_TABLE_NAME+" where "+MRL_URI+" NOT IN (SELECT "+MRL_URI+
                    " from "+MRL_TABLE_NAME+" ORDER BY "+MRL_DATE+" DESC LIMIT "+MRL_TABLE_SIZE+");"+
                    " END";
            db.execSQL(createMrlTableQuery);
        }

        public void dropMRLTableQuery(SQLiteDatabase db) {
            try {
                String query = "DROP TABLE " + MRL_TABLE_NAME + ";";
                db.execSQL(query);
            } catch(SQLiteException e)
            {
                Log.w(TAG, "SQLite tables could not be dropped! Maybe they were missing...");
            }
        }

        private void createHistoryTableQuery(SQLiteDatabase db) {
            String createHistoryTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    HISTORY_TABLE_NAME + " (" +
                    HISTORY_URI + " TEXT PRIMARY KEY NOT NULL,"+
                    HISTORY_TITLE + " TEXT NOT NULL,"+
                    HISTORY_ARTIST + " TEXT,"+
                    HISTORY_TYPE + " INTEGER NOT NULL,"+
                    HISTORY_DATE + " DATETIME NOT NULL"
                    +");";
            db.execSQL(createHistoryTableQuery);
            createHistoryTableQuery = " CREATE TRIGGER history_trigger AFTER INSERT ON "+
                    HISTORY_TABLE_NAME+ " BEGIN "+
                    " DELETE FROM "+HISTORY_TABLE_NAME+" where "+HISTORY_URI+" NOT IN (SELECT "+HISTORY_URI+
                    " from "+HISTORY_TABLE_NAME+" ORDER BY "+HISTORY_DATE+" DESC LIMIT "+HISTORY_TABLE_SIZE+");"+
                    " END";
            db.execSQL(createHistoryTableQuery);
        }

        public void dropHistoryTableQuery(SQLiteDatabase db) {
            try {
                String query = "DROP TABLE " + HISTORY_TABLE_NAME + ";";
                db.execSQL(query);
            } catch(SQLiteException e)
            {
                Log.w(TAG, "SQLite tables could not be dropped! Maybe they were missing...");
            }
        }

        private void createNetworkFavTableQuery(SQLiteDatabase db) {
            String createMrlTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    NETWORK_FAV_TABLE_NAME + " (" +
                    NETWORK_FAV_URI + " TEXT PRIMARY KEY NOT NULL, " +
                    NETWORK_FAV_TITLE + " TEXT NOT NULL, " +
                    NETWORK_FAV_ICON_URL + " TEXT" +
                    ");";
            db.execSQL(createMrlTableQuery);
        }

        public void dropNetworkFavTableQuery(SQLiteDatabase db) {
            try {
                String query = "DROP TABLE " + NETWORK_FAV_TABLE_NAME + ";";
                db.execSQL(query);
            } catch(SQLiteException e) {
                Log.w(TAG, "SQLite tables could not be dropped! Maybe they were missing...");
            }
        }

        private void createExtSubsTableQuery(SQLiteDatabase db) {
            String createMrlTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    EXTERNAL_SUBTITLES_TABLE_NAME + " (" +
                    EXTERNAL_SUBTITLES_URI + " TEXT PRIMARY KEY NOT NULL, " +
                    EXTERNAL_SUBTITLES_MEDIA_NAME + " TEXT NOT NULL" +
                    ");";
            db.execSQL(createMrlTableQuery);
        }

        private void createSlavesTableQuery(SQLiteDatabase db) {
            String createMrlTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    SLAVES_TABLE_NAME + " (" +
                    SLAVES_MEDIA_PATH + " TEXT PRIMARY KEY NOT NULL, " +
                    SLAVES_TYPE + " INTEGER NOT NULL, " +
                    SLAVES_PRIORITY + " INTEGER, " +
                    SLAVES_URI + " TEXT NOT NULL" +
                    ");";
            db.execSQL(createMrlTableQuery);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            String createDirTabelQuery = "CREATE TABLE IF NOT EXISTS "
                    + DIR_TABLE_NAME + " ("
                    + DIR_ROW_PATH + " TEXT PRIMARY KEY NOT NULL"
                    + ");";

            synchronized (this) {
                // Create the directories table
                db.execSQL(createDirTabelQuery);

                // Create the media table
                createMediaTableQuery(db);

                // Create playlist tables
                createPlaylistTablesQuery(db);

                String createSearchhistoryTabelQuery = "CREATE TABLE IF NOT EXISTS "
                        + SEARCHHISTORY_TABLE_NAME + " ("
                        + SEARCHHISTORY_KEY + " VARCHAR(200) PRIMARY KEY NOT NULL, "
                        + SEARCHHISTORY_DATE + " DATETIME NOT NULL"
                        + ");";

                // Create the searchhistory table
                db.execSQL(createSearchhistoryTabelQuery);

                createMRLTableQuery(db);

                createNetworkFavTableQuery(db);

                createHistoryTableQuery(db);

                createExtSubsTableQuery(db);

                createSlavesTableQuery(db);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            synchronized (this) {
                dropMediaTableQuery(db);
                createMediaTableQuery(db);

                // Upgrade incrementally from oldVersion to newVersion
                for(int i = oldVersion+1; i <= newVersion; i++) {
                    switch(i) {
                        case 9:
                            // Remodelled playlist tables: re-create them
                            db.execSQL("DROP TABLE " + PLAYLIST_MEDIA_TABLE_NAME + ";");
                            db.execSQL("DROP TABLE " + PLAYLIST_TABLE_NAME + ";");
                            createPlaylistTablesQuery(db);
                            break;
                        case 11:
                            createMRLTableQuery(db);
                            break;
                        case 13:
                            createNetworkFavTableQuery(db);
                            break;
                        case 17:
                            dropMRLTableQuery(db);
                            createMRLTableQuery(db);
                            break;
                        case 18:
                            dropNetworkFavTableQuery(db);
                            createNetworkFavTableQuery(db);
                            break;
                        case 23:
                            createHistoryTableQuery(db);
                            break;
                        case 24:
                            dropNetworkFavTableQuery(db);
                            createNetworkFavTableQuery(db);
                            break;
                        case 25:
                            createExtSubsTableQuery(db);
                            break;
                        case 26:
                            createSlavesTableQuery(db);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    /**
     * Get all playlists in the database
     *
     * @return An array of all the playlist names
     */
    public synchronized String[] getPlaylists() {
        ArrayList<String> playlists = new ArrayList<String>();
        Cursor c = mDb.query(
                PLAYLIST_TABLE_NAME,
                new String[]{PLAYLIST_NAME},
                null, null, null, null, null);

        if (c != null) {
            while (c.moveToNext())
                playlists.add(c.getString(c.getColumnIndex(PLAYLIST_NAME)));
            c.close();
        }
        return playlists.toArray(new String[playlists.size()]);
    }

    /**
     * Add new playlist
     *
     * @param name Unique name of the playlist
     * @return False if invalid name or already exists, true otherwise
     */
    public synchronized boolean playlistAdd(String name) {
        // Check length
        if(name.length() >= 200)
            return false;

        // Check if already exists
        if(playlistExists(name))
            return false;

        // Create new playlist
        ContentValues values = new ContentValues();
        values.put(PLAYLIST_NAME, name);
        long res = mDb.insert(PLAYLIST_TABLE_NAME, "NULL", values);
        return res != -1;
    }

    /**
     * Delete a playlist and all of its entries.
     *
     * @param name Unique name of the playlist
     */
    public synchronized void playlistDelete(String name) {
        mDb.delete(PLAYLIST_TABLE_NAME, PLAYLIST_NAME + "=?",
                new String[]{name});
        mDb.delete(PLAYLIST_MEDIA_TABLE_NAME, PLAYLIST_MEDIA_PLAYLISTNAME
                + "=?", new String[] { name });
    }

    /**
     * Check if the playlist in question exists.
     *
     * @param name Unique name of the playlist
     * @return true if playlist exists, false otherwise
     */
    public synchronized boolean playlistExists(String name) {
        // Check duplicates
        Cursor c = mDb.query(PLAYLIST_TABLE_NAME,
                new String[] { PLAYLIST_NAME }, PLAYLIST_NAME + "= ?",
                new String[] { name }, null, null, "1");
        if (c != null) {
            final int count = c.getCount();
            c.close();
            return (count > 0);
        } else
            return false;
    }

    /**
     * Get all items in the specified playlist.
     *
     * @param playlistName Unique name of the playlist
     * @return Array containing MRLs of the playlist in order, or null on error
     */
    @Nullable
    public synchronized String[] playlistGetItems(String playlistName) {
        if(!playlistExists(playlistName))
            return null;

        Cursor c = mDb.query(
                PLAYLIST_MEDIA_TABLE_NAME,
                new String[] { PLAYLIST_MEDIA_MEDIALOCATION },
                PLAYLIST_MEDIA_PLAYLISTNAME + "= ?",
                new String[] { playlistName }, null, null,
                PLAYLIST_MEDIA_ORDER + " ASC");

        if (c != null) {
            int count = c.getCount();
            String ret[] = new String[count];
            int i = 0;
            while (c.moveToNext()) {
                ret[i] = c.getString(c.getColumnIndex(PLAYLIST_MEDIA_MEDIALOCATION));
                i++;
            }
            c.close();
            return ret;
        } else
            return null;
    }

    /**
     * Insert an item with location into playlistName at the specified position
     *
     * @param playlistName Unique name of the playlist
     * @param position Position to insert into
     * @param mrl MRL of the media
     */
    public synchronized void playlistInsertItem(String playlistName, int position, String mrl) {
        playlistShiftItems(playlistName, position, 1);

        ContentValues values = new ContentValues();
        values.put(PLAYLIST_MEDIA_PLAYLISTNAME, playlistName);
        values.put(PLAYLIST_MEDIA_MEDIALOCATION, mrl);
        values.put(PLAYLIST_MEDIA_ORDER, position);
        mDb.insert(PLAYLIST_MEDIA_TABLE_NAME, "NULL", values);
    }

    /**
     * Shifts all items starting at position by the given factor.
     *
     * For instance:
     * Before:
     * 0 - A
     * 1 - B
     * 2 - C
     * 3 - D
     *
     * After playlistShiftItems(playlist, 1, 1):
     * 0 - A
     * 2 - B
     * 3 - C
     * 4 - D
     *
     * @param playlistName Unique name of the playlist
     * @param position Position to start shifting at
     * @param factor Factor to shift the order by
     */
    private synchronized void playlistShiftItems(String playlistName, int position, int factor) {
        // Increment all media orders by 1 after the insert position
        Cursor c = mDb.query(
                PLAYLIST_MEDIA_TABLE_NAME,
                new String[] { PLAYLIST_MEDIA_ID, PLAYLIST_MEDIA_ORDER },
                PLAYLIST_MEDIA_PLAYLISTNAME + "=? AND " + PLAYLIST_MEDIA_ORDER + " >= ?",
                new String[] { playlistName, String.valueOf(position) },
                null, null,
                PLAYLIST_MEDIA_ORDER + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                ContentValues cv = new ContentValues();
                int ii = c.getInt(c.getColumnIndex(PLAYLIST_MEDIA_ORDER)) + factor;
                Log.d(TAG, "ii = " + ii);
                cv.put(PLAYLIST_MEDIA_ORDER, ii /* i */);
                mDb.update(PLAYLIST_MEDIA_TABLE_NAME, cv, PLAYLIST_MEDIA_ID + "=?",
                        new String[]{c.getString(c.getColumnIndex(PLAYLIST_MEDIA_ID))});
            }
            c.close();
        }
    }

    /**
     * Removes the item at the given position
     *
     * @param playlistName Unique name of the playlist
     * @param position Position to remove
     */
    public synchronized void playlistRemoveItem(String playlistName, int position) {
        mDb.delete(PLAYLIST_MEDIA_TABLE_NAME,
                PLAYLIST_MEDIA_PLAYLISTNAME + "=? AND " +
                        PLAYLIST_MEDIA_ORDER + "=?",
                new String[]{playlistName, Integer.toString(position)});

        playlistShiftItems(playlistName, position + 1, -1);
    }

    /**
     * Rename the specified playlist.
     *
     * @param playlistName Unique name of the playlist
     * @param newPlaylistName New name of the playlist
     * @return false on error, if playlist doesn't exist or if the new name
     * already exists, true otherwise
     */
    public synchronized boolean playlistRename(String playlistName, String newPlaylistName) {
        if(!playlistExists(playlistName) || playlistExists(newPlaylistName))
            return false;

        // Update playlist table
        ContentValues values = new ContentValues();
        values.put(PLAYLIST_NAME, newPlaylistName);
        mDb.update(PLAYLIST_TABLE_NAME, values, PLAYLIST_NAME + " =?",
                new String[]{playlistName});

        // Update playlist media table
        values = new ContentValues();
        values.put(PLAYLIST_MEDIA_PLAYLISTNAME, newPlaylistName);
        mDb.update(PLAYLIST_MEDIA_TABLE_NAME, values,
                PLAYLIST_MEDIA_PLAYLISTNAME + " =?",
                new String[]{playlistName});

        return true;
    }

    private static void safePut(ContentValues values, String key, String value) {
        if (value == null)
            values.putNull(key);
        else
            values.put(key, value);
    }

    /**
     * Add a new media to the database. The picture can only added by update.
     * @param media which you like to add to the database
     */
    public synchronized void addMedia(MediaWrapper media) {

        ContentValues values = new ContentValues();

        values.put(MEDIA_LOCATION, media.getUri().toString());
        values.put(MEDIA_TIME, media.getTime());
        values.put(MEDIA_LENGTH, media.getLength());
        values.put(MEDIA_TYPE, media.getType());
        values.put(MEDIA_TITLE, media.getTitle());
        safePut(values, MEDIA_ARTIST, media.getArtist());
        safePut(values, MEDIA_GENRE, media.getGenre());
        safePut(values, MEDIA_ALBUM, media.getAlbum());
        safePut(values, MEDIA_ALBUMARTIST, media.getAlbumArtist());
        values.put(MEDIA_WIDTH, media.getWidth());
        values.put(MEDIA_HEIGHT, media.getHeight());
        values.put(MEDIA_ARTWORKURL, media.getArtworkURL());
        values.put(MEDIA_AUDIOTRACK, media.getAudioTrack());
        values.put(MEDIA_SPUTRACK, media.getSpuTrack());
        values.put(MEDIA_TRACKNUMBER, media.getTrackNumber());
        values.put(MEDIA_DISCNUMBER, media.getDiscNumber());
        values.put(MEDIA_LAST_MODIFIED, media.getLastModified());

        mDb.replace(MEDIA_TABLE_NAME, "NULL", values);

    }

    /**
     * Check if the item is already in the database
     * @param location of the item (primary key)
     * @return True if the item exists, false if it does not
     */
    public synchronized boolean mediaItemExists(Uri uri) {
        try {
            Cursor cursor = mDb.query(MEDIA_TABLE_NAME,
                    new String[] { MEDIA_LOCATION },
                    MEDIA_LOCATION + "=?",
                    new String[] { uri.toString() },
                    null, null, null);
            if (cursor != null) {
                final boolean exists = cursor.moveToFirst();
                cursor.close();
                return exists;
            } else
                return false;
        } catch (Exception e) {
            Log.e(TAG, "Query failed");
            return false;
        }
    }

    /**
     * Get all paths from the items in the database
     * @return list of File
     */
    @SuppressWarnings("unused")
    private synchronized HashSet<File> getMediaFiles() {

        HashSet<File> files = new HashSet<File>();
        Cursor cursor;

        cursor = mDb.query(
                MEDIA_TABLE_NAME,
                new String[] { MEDIA_LOCATION },
                null, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                do {
                    File file = new File(cursor.getString(0));
                    files.add(file);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return files;
    }

    public synchronized Cursor queryMedia(String query){
        String[] queryColumns = new String[]{MEDIA_LOCATION, MEDIA_TITLE};
        return mDb.query(MEDIA_VIRTUAL_TABLE_NAME, queryColumns, MEDIA_VIRTUAL_TABLE_NAME+" MATCH ?",
                new String[]{query + "*"}, null, null, null, null);
    }

    public synchronized ArrayList<String> searchMedia(String filter){

        ArrayList<String> mediaList = new ArrayList<String>();
        Cursor cursor = queryMedia(filter);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    mediaList.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return mediaList;
    }

    public synchronized ArrayMap<String, MediaWrapper> getMedias() {

        Cursor cursor;
        ArrayMap<String, MediaWrapper> medias = new ArrayMap<String, MediaWrapper>();
        int chunk_count = 0;
        int count;

        do {
            count = 0;
            cursor = mDb.rawQuery(String.format(Locale.US,
                    "SELECT %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s FROM %s LIMIT %d OFFSET %d",
                    MEDIA_LOCATION, //0 string
                    MEDIA_TIME, //1 long
                    MEDIA_LENGTH, //2 long
                    MEDIA_TYPE, //3 int
                    MEDIA_TITLE, //4 string
                    MEDIA_ARTIST, //5 string
                    MEDIA_GENRE, //6 string
                    MEDIA_ALBUM, //7 string
                    MEDIA_ALBUMARTIST, //8 string
                    MEDIA_WIDTH, //9 int
                    MEDIA_HEIGHT, //10 int
                    MEDIA_ARTWORKURL, //11 string
                    MEDIA_AUDIOTRACK, //12 int
                    MEDIA_SPUTRACK, //13 int
                    MEDIA_TRACKNUMBER, // 14 int
                    MEDIA_DISCNUMBER, //15 int
                    MEDIA_LAST_MODIFIED, //16 long
                    MEDIA_TABLE_NAME,
                    CHUNK_SIZE,
                    chunk_count * CHUNK_SIZE), null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    try {
                        do {
                            final Uri uri = AndroidUtil.LocationToUri(cursor.getString(0));
                            MediaWrapper media = new MediaWrapper(uri,
                                    cursor.getLong(1),      // MEDIA_TIME
                                    cursor.getLong(2),      // MEDIA_LENGTH
                                    cursor.getInt(3),       // MEDIA_TYPE
                                    null,                   // MEDIA_PICTURE
                                    cursor.getString(4),    // MEDIA_TITLE
                                    cursor.getString(5),    // MEDIA_ARTIST
                                    cursor.getString(6),    // MEDIA_GENRE
                                    cursor.getString(7),    // MEDIA_ALBUM
                                    cursor.getString(8),    // MEDIA_ALBUMARTIST
                                    cursor.getInt(9),       // MEDIA_WIDTH
                                    cursor.getInt(10),       // MEDIA_HEIGHT
                                    cursor.getString(11),   // MEDIA_ARTWORKURL
                                    cursor.getInt(12),      // MEDIA_AUDIOTRACK
                                    cursor.getInt(13),      // MEDIA_SPUTRACK
                                    cursor.getInt(14),      // MEDIA_TRACKNUMBER
                                    cursor.getInt(15),     // MEDIA_DISCNUMBER
                                    cursor.getLong(16));     // MEDIA_LAST_MODIFIED
                            medias.put(media.getUri().toString(), media);

                            count++;
                        } while (cursor.moveToNext());
                    } catch (IllegalStateException e) {
                    } //Google bug causing IllegalStateException, see https://code.google.com/p/android/issues/detail?id=32472
                }

                cursor.close();
            }
            chunk_count++;
        } while (count == CHUNK_SIZE);

        return medias;
    }

    public synchronized ArrayMap<String, Long> getVideoTimes() {

        Cursor cursor;
        ArrayMap<String, Long> times = new ArrayMap<String, Long>();
        int chunk_count = 0;
        int count;

        do {
            count = 0;
            cursor = mDb.rawQuery(String.format(Locale.US,
                    "SELECT %s,%s FROM %s WHERE %s=%d LIMIT %d OFFSET %d",
                    MEDIA_LOCATION, //0 string
                    MEDIA_TIME, //1 long
                    MEDIA_TABLE_NAME,
                    MEDIA_TYPE,
                    MediaWrapper.TYPE_VIDEO,
                    CHUNK_SIZE,
                    chunk_count * CHUNK_SIZE), null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        String location = cursor.getString(0);
                        long time = cursor.getLong(1);
                        times.put(location, time);
                        count++;
                    } while (cursor.moveToNext());
                }

                cursor.close();
            }
            chunk_count++;
        } while (count == CHUNK_SIZE);

        return times;
    }

    public synchronized MediaWrapper getMedia(Uri uri) {

        Cursor cursor;
        MediaWrapper media = null;

        try {
            cursor = mDb.query(
                    MEDIA_TABLE_NAME,
                    new String[] {
                            MEDIA_TIME, //0 long
                            MEDIA_LENGTH, //1 long
                            MEDIA_TYPE, //2 int
                            MEDIA_TITLE, //3 string
                            MEDIA_ARTIST, //4 string
                            MEDIA_GENRE, //5 string
                            MEDIA_ALBUM, //6 string
                            MEDIA_ALBUMARTIST, //7 string
                            MEDIA_WIDTH, //8 int
                            MEDIA_HEIGHT, //9 int
                            MEDIA_ARTWORKURL, //10 string
                            MEDIA_AUDIOTRACK, //11 int
                            MEDIA_SPUTRACK, //12 int
                            MEDIA_TRACKNUMBER, //13 int
                            MEDIA_DISCNUMBER, //14 int
                            MEDIA_LAST_MODIFIED, //15 long
                    },
                    MEDIA_LOCATION + "=?",
                    new String[] { uri.toString() },
                    null, null, null);
        } catch(IllegalArgumentException e) {
            // java.lang.IllegalArgumentException: the bind value at index 1 is null
            return null;
        }
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                media = new MediaWrapper(uri,
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getInt(2),
                        null, // lazy loading, see getPicture()
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getString(7),
                        cursor.getInt(8),
                        cursor.getInt(9),
                        cursor.getString(10),
                        cursor.getInt(11),
                        cursor.getInt(12),
                        cursor.getInt(13),
                        cursor.getInt(14),
                        cursor.getLong(15));
            }
            cursor.close();
        }
        return media;
    }

    public synchronized Bitmap getPicture(Uri uri) {
        /* Used for the lazy loading */
        Cursor cursor;
        Bitmap picture = null;
        byte[] blob;

        cursor = mDb.query(
                MEDIA_TABLE_NAME,
                new String[] { MEDIA_PICTURE },
                MEDIA_LOCATION + "=?",
                new String[] { uri.toString() },
                null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                blob = cursor.getBlob(0);
                if (blob != null && blob.length > 1 && blob.length < 500000) {
                    try {
                        picture = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                    } catch (OutOfMemoryError e) {
                        picture = null;
                    } finally {
                        blob = null;
                    }
                }
            }
            cursor.close();
        }
        return picture;
    }

    public synchronized void removeMedia(Uri uri) {
        try {
            mDb.delete(MEDIA_TABLE_NAME, MEDIA_LOCATION + "=?", new String[]{uri.toString()});
        } catch (SQLiteException e) {
            // Some devices have weird issues with FTS table
        }
    }

    public synchronized void removeMedias(Collection<Uri> uris) {
        mDb.beginTransaction();
        try {
            for (Uri uri : uris)
                removeMedia(uri);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    public synchronized void removeMediaWrappers(Collection<MediaWrapper> mws) {
        mDb.beginTransaction();
        try {
            for (MediaWrapper mw : mws)
                removeMedia(mw.getUri());
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    public synchronized void updateMedia(Uri uri, int col,
                                         Object object) {

        if (uri == null)
            return;

        ContentValues values = new ContentValues();
        switch (col) {
            case INDEX_MEDIA_PICTURE:
                if (object != null) {
                    Bitmap picture = (Bitmap) object;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    picture.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    values.put(MEDIA_PICTURE, out.toByteArray());
                }
                else {
                    values.put(MEDIA_PICTURE, new byte[1]);
                }
                break;
            case INDEX_MEDIA_TIME:
                if (object != null)
                    values.put(MEDIA_TIME, (Long)object);
                break;
            case INDEX_MEDIA_AUDIOTRACK:
                if (object != null)
                    values.put(MEDIA_AUDIOTRACK, (Integer)object);
                break;
            case INDEX_MEDIA_SPUTRACK:
                if (object != null)
                    values.put(MEDIA_SPUTRACK, (Integer)object);
                break;
            case INDEX_MEDIA_LENGTH:
                if (object != null)
                    values.put(MEDIA_LENGTH, (Long)object);
                break;
            default:
                return;
        }
        mDb.update(MEDIA_TABLE_NAME, values, MEDIA_LOCATION + "=?", new String[]{uri.toString()});
    }

    /**
     * Add directory to the directories table
     *
     * @param path
     */
    public synchronized void addDir(String path) {
        ContentValues values = new ContentValues();
        values.put(DIR_ROW_PATH, path);
        mDb.insert(DIR_TABLE_NAME, null, values);
    }

    /**
     * Delete directory from directories table
     *
     * @param path
     */
    public synchronized void removeDir(String path) {
        mDb.delete(DIR_TABLE_NAME, DIR_ROW_PATH + "=?", new String[]{path});
    }

    /**
     * Delete all matching directories from directories table
     *
     * @param path
     */
    public synchronized void recursiveRemoveDir(String path) {
        for(File f : getMediaDirs()) {
            final String dirPath = f.getPath();
            if(dirPath.startsWith(path))
                mDb.delete(DIR_TABLE_NAME, DIR_ROW_PATH + "=?", new String[] { dirPath });
        }

    }

    /**
     *
     * @return
     */
    public synchronized List<File> getMediaDirs() {

        List<File> paths = new ArrayList<File>();
        Cursor cursor;

        cursor = mDb.query(
                DIR_TABLE_NAME,
                new String[] { DIR_ROW_PATH },
                null, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                do {
                    File dir = new File(cursor.getString(0));
                    paths.add(dir);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return paths;
    }

    private synchronized boolean mediaDirExists(String path) {
        Cursor cursor = mDb.query(DIR_TABLE_NAME,
                new String[] { DIR_ROW_PATH },
                DIR_ROW_PATH + "=?",
                new String[] { path },
                null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     *
     * @param key
     */
    public synchronized void addSearchhistoryItem(String key) {
        // set the format to sql date time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date date = new Date();
        ContentValues values = new ContentValues();
        values.put(SEARCHHISTORY_KEY, key);
        values.put(SEARCHHISTORY_DATE, dateFormat.format(date));

        mDb.replace(SEARCHHISTORY_TABLE_NAME, null, values);
    }

    public synchronized ArrayList<String> getSearchhistory(int size) {
        ArrayList<String> history = new ArrayList<String>();

        Cursor cursor = mDb.query(SEARCHHISTORY_TABLE_NAME,
                new String[]{SEARCHHISTORY_KEY},
                null, null, null, null,
                SEARCHHISTORY_DATE + " DESC",
                Integer.toString(size));

        while (cursor.moveToNext()) {
            history.add(cursor.getString(0));
        }
        cursor.close();

        return history;
    }

    public synchronized void clearSearchHistory() {
        mDb.delete(SEARCHHISTORY_TABLE_NAME, null, null);
    }

    public synchronized void addMrlhistoryItem(String uri) {
        // set the format to sql date time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date date = new Date();
        ContentValues values = new ContentValues();
        values.put(MRL_URI, uri);
        values.put(MRL_DATE, dateFormat.format(date));

        mDb.replace(MRL_TABLE_NAME, null, values);
    }

    public synchronized ArrayList<String> getMrlhistory() {
        ArrayList<String> history = new ArrayList<String>();

        Cursor cursor = mDb.query(MRL_TABLE_NAME,
                new String[] { MRL_URI },
                null, null, null, null,
                MRL_DATE + " DESC",
                MRL_TABLE_SIZE);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                history.add(cursor.getString(0));
            }
            cursor.close();
        }

        return history;
    }

    public synchronized void deleteMrlUri(String uri) {
        mDb.delete(MRL_TABLE_NAME, MRL_URI + "=?", new String[]{uri});
    }

    public synchronized void clearMrlHistory() {
        mDb.delete(MRL_TABLE_NAME, null, null);
    }

    /**
     * Playback history management
     */

    public synchronized void addHistoryItem(MediaWrapper mw) {
        // set the format to sql date time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date date = new Date();
        ContentValues values = new ContentValues();
        values.put(HISTORY_URI, mw.getUri().toString());
        values.put(HISTORY_TITLE, mw.getTitle());
        values.put(HISTORY_ARTIST, mw.getArtist());
        values.put(HISTORY_TYPE, mw.getType());
        values.put(HISTORY_DATE, dateFormat.format(date));

        mDb.replace(HISTORY_TABLE_NAME, null, values);
    }

    public synchronized ArrayList<MediaWrapper> getHistory() {
        ArrayList<MediaWrapper> history = new ArrayList<>();

        Cursor cursor = mDb.query(HISTORY_TABLE_NAME,
                new String[] { HISTORY_URI , HISTORY_TITLE, HISTORY_ARTIST, HISTORY_TYPE, HISTORY_DATE},
                null, null, null, null,
                HISTORY_DATE + " DESC",
                HISTORY_TABLE_SIZE);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                MediaWrapper mw = new MediaWrapper(Uri.parse(cursor.getString(0)));
                mw.setDisplayTitle(cursor.getString(1));
                mw.setArtist(cursor.getString(2));
                mw.setType(cursor.getInt(3));
                history.add(mw);
            }
            cursor.close();
        }

        return history;
    }

    public synchronized void deleteHistoryUri(String uri) {
        mDb.delete(HISTORY_TABLE_NAME, HISTORY_URI + "=?", new String[]{uri});
    }

    public synchronized void clearHistory() {
        mDb.delete(HISTORY_TABLE_NAME, null, null);
    }


    /**
     * Network favorites management
     */

    public synchronized void addNetworkFavItem(Uri uri, String title, String iconUrl) {
        ContentValues values = new ContentValues();
        values.put(NETWORK_FAV_URI, uri.toString());
        values.put(NETWORK_FAV_TITLE, Uri.encode(title));
        values.put(NETWORK_FAV_ICON_URL, Uri.encode(iconUrl));
        mDb.replace(NETWORK_FAV_TABLE_NAME, null, values);
    }

    public synchronized boolean networkFavExists(Uri uri) {
        Cursor cursor = mDb.query(NETWORK_FAV_TABLE_NAME,
                new String[] { NETWORK_FAV_URI },
                NETWORK_FAV_URI + "=?",
                new String[] { uri.toString() },
                null, null, null);
        if (cursor != null) {
            final boolean exists = cursor.moveToFirst();
            cursor.close();
            return exists;
        } else
            return false;
    }

    public synchronized ArrayList<MediaWrapper> getAllNetworkFav() {
        ArrayList<MediaWrapper> favs = new ArrayList<MediaWrapper>();

        MediaWrapper mw;
        Cursor cursor = mDb.query(NETWORK_FAV_TABLE_NAME,
                new String[] { NETWORK_FAV_URI , NETWORK_FAV_TITLE, NETWORK_FAV_ICON_URL},
                null, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                mw = new MediaWrapper(Uri.parse(cursor.getString(0)));
                mw.setDisplayTitle(Uri.decode(cursor.getString(1)));
                mw.setType(MediaWrapper.TYPE_DIR);
                String url = cursor.getString(2);
                if (!TextUtils.isEmpty(url))
                    mw.setArtworkURL(Uri.decode(url));
                favs.add(mw);
            }
            cursor.close();
        }

        return favs;
    }

    public synchronized void deleteNetworkFav(Uri uri) {
        mDb.delete(NETWORK_FAV_TABLE_NAME, NETWORK_FAV_URI + "=?", new String[] { uri.toString() });
    }

    public synchronized void clearNetworkFavTable() {
        mDb.delete(NETWORK_FAV_TABLE_NAME, null, null);
    }

    /**
     * External subtitles management
     */

    public synchronized void saveSubtitle(String path, String mediaName) {
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mediaName))
            return;
        ContentValues values = new ContentValues();
        values.put(EXTERNAL_SUBTITLES_URI, path);
        values.put(EXTERNAL_SUBTITLES_MEDIA_NAME, mediaName);
        mDb.replace(EXTERNAL_SUBTITLES_TABLE_NAME, null, values);
    }

    public synchronized ArrayList<String> getSubtitles(String mediaName) {
        Cursor cursor = mDb.query(EXTERNAL_SUBTITLES_TABLE_NAME,
                new String[] {EXTERNAL_SUBTITLES_MEDIA_NAME, EXTERNAL_SUBTITLES_URI },
                EXTERNAL_SUBTITLES_MEDIA_NAME + "=?",
                new String[] { mediaName },
                null, null, null);
        ArrayList<String> list = new ArrayList<>(cursor.getCount());
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String url = cursor.getString(1);
                if (!TextUtils.isEmpty(url)) {
                    String fileUrl = Uri.decode(url);
                    if (new File(fileUrl).exists())
                        list.add(fileUrl);
                    else
                        deleteSubtitle(url);
                }
            }
            cursor.close();
        }
        return list;
    }

    public synchronized void deleteSubtitle(String path) {
        mDb.delete(EXTERNAL_SUBTITLES_TABLE_NAME, EXTERNAL_SUBTITLES_URI + "=?", new String[] { path });
    }

    public synchronized void clearExternalSubtitlesTable() {
        mDb.delete(EXTERNAL_SUBTITLES_TABLE_NAME, null, null);
    }

    /**
     * slaves management
     */

    public synchronized void saveSlave(String mediaPath, int type, int priority, String uriString) {
        ContentValues values = new ContentValues();
        values.put(SLAVES_MEDIA_PATH, mediaPath);
        values.put(SLAVES_TYPE, type);
        values.put(SLAVES_PRIORITY, priority);
        values.put(SLAVES_URI, uriString);
        mDb.replace(SLAVES_TABLE_NAME, null, values);
    }

    public synchronized void saveSlaves(MediaWrapper mw) {
        for (Media.Slave slave : mw.getSlaves())
            saveSlave(mw.getLocation(), slave.type, slave.priority, slave.uri);
    }

    public synchronized ArrayList<Media.Slave> getSlaves(String mrl) {
        Cursor cursor = mDb.query(SLAVES_TABLE_NAME,
                new String[] {SLAVES_MEDIA_PATH, SLAVES_TYPE, SLAVES_PRIORITY, SLAVES_URI },
                SLAVES_MEDIA_PATH + "=?",
                new String[] { mrl },
                null, null, null);
        ArrayList<Media.Slave> list = new ArrayList<>(cursor.getCount());
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String uri = cursor.getString(3);
                if (!TextUtils.isEmpty(uri)) {
                    uri = Uri.decode(uri);
                list.add(new Media.Slave(cursor.getInt(1), cursor.getInt(2), uri));
                }
            }
            cursor.close();
        }
        return list;
    }

    public synchronized void deleteSlaves(String mrl) {
        mDb.delete(SLAVES_TABLE_NAME, SLAVES_MEDIA_PATH + "=?", new String[] { mrl });
    }

    public synchronized void clearSlavesTable() {
        mDb.delete(SLAVES_TABLE_NAME, null, null);
    }

    /**
     * Empty the database for debugging purposes
     */

    public synchronized void emptyDatabase() {
        mDb.delete(MEDIA_TABLE_NAME, null, null);
    }

    public static void setPicture(MediaWrapper m, Bitmap p) {
        Log.d(TAG, "Setting new picture for " + m.getTitle());
        try {
            getInstance().updateMedia(
                    m.getUri(),
                    INDEX_MEDIA_PICTURE,
                    p);
        } catch (SQLiteFullException e) {
            Log.d(TAG, "SQLiteFullException while setting picture");
        }
        m.setPictureParsed(true);
    }
}
