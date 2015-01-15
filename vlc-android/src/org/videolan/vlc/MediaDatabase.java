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

package org.videolan.vlc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MediaDatabase {
    public final static String TAG = "VLC/MediaDatabase";

    private static MediaDatabase instance;

    private SQLiteDatabase mDb;
    private final String DB_NAME = "vlc_database";
    private final int DB_VERSION = 12;
    private final int CHUNK_SIZE = 50;

    private final String DIR_TABLE_NAME = "directories_table";
    private final String DIR_ROW_PATH = "path";

    private final String MEDIA_TABLE_NAME = "media_table";
    private final String MEDIA_LOCATION = "location";
    private final String MEDIA_TIME = "time";
    private final String MEDIA_LENGTH = "length";
    private final String MEDIA_TYPE = "type";
    private final String MEDIA_PICTURE = "picture";
    private final String MEDIA_TITLE = "title";
    private final String MEDIA_ARTIST = "artist";
    private final String MEDIA_GENRE = "genre";
    private final String MEDIA_ALBUM = "album";
    private final String MEDIA_ALBUMARTIST = "albumartist";
    private final String MEDIA_WIDTH = "width";
    private final String MEDIA_HEIGHT = "height";
    private final String MEDIA_ARTWORKURL = "artwork_url";
    private final String MEDIA_AUDIOTRACK = "audio_track";
    private final String MEDIA_SPUTRACK = "spu_track";
    private final String MEDIA_TRACKNUMBER = "track_number";

    private final String PLAYLIST_TABLE_NAME = "playlist_table";
    private final String PLAYLIST_NAME = "name";

    private final String PLAYLIST_MEDIA_TABLE_NAME = "playlist_media_table";
    private final String PLAYLIST_MEDIA_ID = "id";
    private final String PLAYLIST_MEDIA_PLAYLISTNAME = "playlist_name";
    private final String PLAYLIST_MEDIA_MEDIALOCATION = "media_location";
    private final String PLAYLIST_MEDIA_ORDER = "playlist_order";

    private final String SEARCHHISTORY_TABLE_NAME = "searchhistory_table";
    private final String SEARCHHISTORY_DATE = "date";
    private final String SEARCHHISTORY_KEY = "key";

    private final String MRL_TABLE_NAME = "mrl_table";
    private final String MRL_DATE = "date";
    private final String MRL_URI = "uri";
    private final String MRL_TABLE_SIZE = "100";

    public enum mediaColumn {
        MEDIA_TABLE_NAME, MEDIA_PATH, MEDIA_TIME, MEDIA_LENGTH,
        MEDIA_TYPE, MEDIA_PICTURE, MEDIA_TITLE, MEDIA_ARTIST, MEDIA_GENRE, MEDIA_ALBUM,
        MEDIA_ALBUMARTIST, MEDIA_WIDTH, MEDIA_HEIGHT, MEDIA_ARTWORKURL, MEDIA_AUDIOTRACK,
        MEDIA_SPUTRACK, MEDIA_TRACKNUMBER
    }

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

    private class DatabaseHelper extends SQLiteOpenHelper {

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
            String query = "DROP TABLE " + MEDIA_TABLE_NAME + ";";
            db.execSQL(query);
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
                    + MEDIA_TRACKNUMBER + " INTEGER"
                    + ");";
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
            createMrlTableQuery += " CREATE TRIGGER mrl_history_trigger AFTER INSERT ON "+
                    MRL_TABLE_NAME+ " BEGIN "+
                    " DELETE FROM "+MRL_TABLE_NAME+" where "+MRL_URI+" NOT IN (SELECT "+MRL_URI+
                    " from "+MRL_TABLE_NAME+" ORDER BY insertion_date DESC LIMIT "+MRL_TABLE_SIZE+");"+
                    " END";
            db.execSQL(createMrlTableQuery);
        }

        public void dropMRLTableQuery(SQLiteDatabase db) {
            String query = "DROP TABLE " + MRL_TABLE_NAME + ";";
            db.execSQL(query);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            String createDirTabelQuery = "CREATE TABLE IF NOT EXISTS "
                    + DIR_TABLE_NAME + " ("
                    + DIR_ROW_PATH + " TEXT PRIMARY KEY NOT NULL"
                    + ");";

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
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < DB_VERSION && newVersion == DB_VERSION) {
                dropMediaTableQuery(db);
                createMediaTableQuery(db);
            }
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
                default:
                    break;
                }
            }
        }
    }

    /**
     * Get all playlists in the database
     *
     * @return An array of all the playlist names
     */
    public String[] getPlaylists() {
        ArrayList<String> playlists = new ArrayList<String>();
        Cursor c = mDb.query(
                PLAYLIST_TABLE_NAME,
                new String[] { PLAYLIST_NAME },
                null, null, null, null, null);

        while(c.moveToNext())
            playlists.add(c.getString(c.getColumnIndex(PLAYLIST_NAME)));
        c.close();
        return playlists.toArray(new String[playlists.size()]);
    }

    /**
     * Add new playlist
     *
     * @param name Unique name of the playlist
     * @return False if invalid name or already exists, true otherwise
     */
    public boolean playlistAdd(String name) {
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
    public void playlistDelete(String name) {
        mDb.delete(PLAYLIST_TABLE_NAME, PLAYLIST_NAME + "=?",
                new String[] { name });
        mDb.delete(PLAYLIST_MEDIA_TABLE_NAME, PLAYLIST_MEDIA_PLAYLISTNAME
                + "=?", new String[] { name });
    }

    /**
     * Check if the playlist in question exists.
     *
     * @param name Unique name of the playlist
     * @return true if playlist exists, false otherwise
     */
    public boolean playlistExists(String name) {
        // Check duplicates
        Cursor c = mDb.query(PLAYLIST_TABLE_NAME,
                new String[] { PLAYLIST_NAME }, PLAYLIST_NAME + "= ?",
                new String[] { name }, null, null, "1");
        int count = c.getCount();
        c.close();
        return (count > 0);
    }

   /**
     * Get all items in the specified playlist.
     *
     * @param playlistName Unique name of the playlist
     * @return Array containing MRLs of the playlist in order, or null on error
     */
    public String[] playlistGetItems(String playlistName) {
        if(!playlistExists(playlistName))
            return null;

        Cursor c = mDb.query(
                PLAYLIST_MEDIA_TABLE_NAME,
                new String[] { PLAYLIST_MEDIA_MEDIALOCATION },
                PLAYLIST_MEDIA_PLAYLISTNAME + "= ?",
                new String[] { playlistName }, null, null,
                PLAYLIST_MEDIA_ORDER + " ASC");

        int count = c.getCount();
        String ret[] = new String[count]; int i = 0;
        while(c.moveToNext()) {
            ret[i] = c.getString(c.getColumnIndex(PLAYLIST_MEDIA_MEDIALOCATION));
            i++;
        }
        c.close();
        return ret;
    }

    /**
     * Insert an item with location into playlistName at the specified position
     *
     * @param playlistName Unique name of the playlist
     * @param position Position to insert into
     * @param mrl MRL of the media
     */
    public void playlistInsertItem(String playlistName, int position, String mrl) {
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
    private void playlistShiftItems(String playlistName, int position, int factor) {
        // Increment all media orders by 1 after the insert position
        Cursor c = mDb.query(
                PLAYLIST_MEDIA_TABLE_NAME,
                new String[] { PLAYLIST_MEDIA_ID, PLAYLIST_MEDIA_ORDER },
                PLAYLIST_MEDIA_PLAYLISTNAME + "=? AND " + PLAYLIST_MEDIA_ORDER + " >= ?",
                new String[] { playlistName, String.valueOf(position) },
                null, null,
                PLAYLIST_MEDIA_ORDER + " ASC");
        while(c.moveToNext()) {
            ContentValues cv = new ContentValues();
            int ii = c.getInt(c.getColumnIndex(PLAYLIST_MEDIA_ORDER)) + factor;
            Log.d(TAG, "ii = " + ii);
            cv.put(PLAYLIST_MEDIA_ORDER, ii /* i */);
            mDb.update(PLAYLIST_MEDIA_TABLE_NAME, cv, PLAYLIST_MEDIA_ID + "=?",
                    new String[] { c.getString(c.getColumnIndex(PLAYLIST_MEDIA_ID)) });
        }
    }

    /**
     * Removes the item at the given position
     *
     * @param playlistName Unique name of the playlist
     * @param position Position to remove
     */
    public void playlistRemoveItem(String playlistName, int position) {
        mDb.delete(PLAYLIST_MEDIA_TABLE_NAME,
                PLAYLIST_MEDIA_PLAYLISTNAME + "=? AND " +
                PLAYLIST_MEDIA_ORDER + "=?",
                new String[] { playlistName, Integer.toString(position) });

        playlistShiftItems(playlistName, position+1, -1);
    }

    /**
     * Rename the specified playlist.
     *
     * @param playlistName Unique name of the playlist
     * @param newPlaylistName New name of the playlist
     * @return false on error, if playlist doesn't exist or if the new name
     * already exists, true otherwise
     */
    public boolean playlistRename(String playlistName, String newPlaylistName) {
        if(!playlistExists(playlistName) || playlistExists(newPlaylistName))
            return false;

        // Update playlist table
        ContentValues values = new ContentValues();
        values.put(PLAYLIST_NAME, newPlaylistName);
        mDb.update(PLAYLIST_TABLE_NAME, values, PLAYLIST_NAME + " =?",
                new String[] { playlistName });

        // Update playlist media table
        values = new ContentValues();
        values.put(PLAYLIST_MEDIA_PLAYLISTNAME, newPlaylistName);
        mDb.update(PLAYLIST_MEDIA_TABLE_NAME, values,
                PLAYLIST_MEDIA_PLAYLISTNAME + " =?",
                new String[] { playlistName });

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

        values.put(MEDIA_LOCATION, media.getLocation());
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

        mDb.replace(MEDIA_TABLE_NAME, "NULL", values);

    }

    /**
     * Check if the item is already in the database
     * @param location of the item (primary key)
     * @return True if the item exists, false if it does not
     */
    public synchronized boolean mediaItemExists(String location) {
        try {
            Cursor cursor = mDb.query(MEDIA_TABLE_NAME,
                    new String[] { MEDIA_LOCATION },
                    MEDIA_LOCATION + "=?",
                    new String[] { location },
                    null, null, null);
            boolean exists = cursor.moveToFirst();
            cursor.close();
            return exists;
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
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            do {
                File file = new File(cursor.getString(0));
                files.add(file);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return files;
    }

    public synchronized ArrayList<String> searchMedia(String filter, int type){

        ArrayList<String> mediaList = new ArrayList<String>();

        String[] queryColumns = new String[]{MEDIA_LOCATION, MEDIA_TITLE, MEDIA_ALBUM, MEDIA_ARTIST, MEDIA_TYPE};
        String queryString = MEDIA_TITLE+" LIKE ? OR "+MEDIA_ALBUM+" LIKE ? OR "+MEDIA_ARTIST+" LIKE ?";
        String [] queryArgs;
        if (type != MediaWrapper.TYPE_ALL) {
            queryString = "( " + queryString + " ) AND " + MEDIA_TYPE + "=?";
            queryArgs = new String[]{"%"+filter+"%", "%"+filter+"%", "%"+filter+"%", String.valueOf(type)};
        } else
            queryArgs = new String[]{"%"+filter+"%", "%"+filter+"%", "%"+filter+"%"};

        Cursor cursor = mDb.query(MEDIA_TABLE_NAME,
                queryColumns, queryString, queryArgs, null, null, null, null);
        if (cursor.moveToFirst()){
            do {
                mediaList.add(cursor.getString(0));
            }while (cursor.moveToNext());
            cursor.close();
        }
        return mediaList;
    }

    public synchronized HashMap<String, MediaWrapper> getMedias() {

        Cursor cursor;
        HashMap<String, MediaWrapper> medias = new HashMap<String, MediaWrapper>();
        int chunk_count = 0;
        int count = 0;

        do {
            count = 0;
            cursor = mDb.rawQuery(String.format(Locale.US,
                    "SELECT %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s FROM %s LIMIT %d OFFSET %d",
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
                    MEDIA_TRACKNUMBER, // 13 int
                    MEDIA_LOCATION, //14 string
                    MEDIA_TABLE_NAME,
                    CHUNK_SIZE,
                    chunk_count * CHUNK_SIZE), null);

            if (cursor.moveToFirst()) {
                do {
                    String location = cursor.getString(14);
                    MediaWrapper media = new MediaWrapper(location,
                            cursor.getLong(0),      // MEDIA_TIME
                            cursor.getLong(1),      // MEDIA_LENGTH
                            cursor.getInt(2),       // MEDIA_TYPE
                            null,                   // MEDIA_PICTURE
                            cursor.getString(3),    // MEDIA_TITLE
                            cursor.getString(4),    // MEDIA_ARTIST
                            cursor.getString(5),    // MEDIA_GENRE
                            cursor.getString(6),    // MEDIA_ALBUM
                            cursor.getString(7),    // MEDIA_ALBUMARTIST
                            cursor.getInt(8),       // MEDIA_WIDTH
                            cursor.getInt(9),       // MEDIA_HEIGHT
                            cursor.getString(10),   // MEDIA_ARTWORKURL
                            cursor.getInt(11),      // MEDIA_AUDIOTRACK
                            cursor.getInt(12),      // MEDIA_SPUTRACK
                            cursor.getInt(13));     // MEDIA_TRACKNUMBER
                    medias.put(media.getLocation(), media);

                    count++;
                } while (cursor.moveToNext());
            }

            cursor.close();
            chunk_count++;
        } while (count == CHUNK_SIZE);

        return medias;
    }

    public synchronized HashMap<String, Long> getVideoTimes(Context context) {

        Cursor cursor;
        HashMap<String, Long> times = new HashMap<String, Long>();
        int chunk_count = 0;
        int count = 0;

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

            if (cursor.moveToFirst()) {
                do {
                    String location = cursor.getString(0);
                    long time = cursor.getLong(1);
                    times.put(location, time);
                    count++;
                } while (cursor.moveToNext());
            }

            cursor.close();
            chunk_count++;
        } while (count == CHUNK_SIZE);

        return times;
    }

    public synchronized MediaWrapper getMedia(String location) {

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
                },
                MEDIA_LOCATION + "=?",
                new String[] { location },
                null, null, null);
        } catch(IllegalArgumentException e) {
            // java.lang.IllegalArgumentException: the bind value at index 1 is null
            return null;
        }
        if (cursor.moveToFirst()) {
            media = new MediaWrapper(location,
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
                    cursor.getInt(13));
        }
        cursor.close();
        return media;
    }

    public synchronized Bitmap getPicture(Context context, String location) {
        /* Used for the lazy loading */
        Cursor cursor;
        Bitmap picture = null;
        byte[] blob;

        cursor = mDb.query(
                MEDIA_TABLE_NAME,
                new String[] { MEDIA_PICTURE },
                MEDIA_LOCATION + "=?",
                new String[] { location },
                null, null, null);
        if (cursor.moveToFirst()) {
            blob = cursor.getBlob(0);
            if (blob != null && blob.length > 1 && blob.length < 500000) {
                try {
                    picture = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                } catch(OutOfMemoryError e) {
                    picture = null;
                } finally {
                    blob = null;
                }
            }
        }
        cursor.close();
        return picture;
    }

    public synchronized void removeMedia(String location) {
        mDb.delete(MEDIA_TABLE_NAME, MEDIA_LOCATION + "=?", new String[] { location });
    }

    public void removeMedias(Set<String> locations) {
        mDb.beginTransaction();
        try {
            for (String location : locations)
                mDb.delete(MEDIA_TABLE_NAME, MEDIA_LOCATION + "=?", new String[] { location });
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    public synchronized void updateMedia(String location, mediaColumn col,
            Object object) {

        if (location == null)
            return;

        ContentValues values = new ContentValues();
        switch (col) {
            case MEDIA_PICTURE:
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
            case MEDIA_TIME:
                if (object != null)
                    values.put(MEDIA_TIME, (Long)object);
                break;
            case MEDIA_AUDIOTRACK:
                if (object != null)
                    values.put(MEDIA_AUDIOTRACK, (Integer)object);
                break;
            case MEDIA_SPUTRACK:
                if (object != null)
                    values.put(MEDIA_SPUTRACK, (Integer)object);
                break;
            case MEDIA_LENGTH:
                if (object != null)
                    values.put(MEDIA_LENGTH, (Long)object);
                break;
            default:
                return;
        }
        mDb.update(MEDIA_TABLE_NAME, values, MEDIA_LOCATION + "=?", new String[] { location });
    }

    /**
     * Add directory to the directories table
     *
     * @param path
     */
    public synchronized void addDir(String path) {
        if (!mediaDirExists(path)) {
            ContentValues values = new ContentValues();
            values.put(DIR_ROW_PATH, path);
            mDb.insert(DIR_TABLE_NAME, null, values);
        }
    }

    /**
     * Delete directory from directories table
     *
     * @param path
     */
    public synchronized void removeDir(String path) {
        mDb.delete(DIR_TABLE_NAME, DIR_ROW_PATH + "=?", new String[] { path });
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
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            do {
                File dir = new File(cursor.getString(0));
                paths.add(dir);
            } while (cursor.moveToNext());
        }
        cursor.close();

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
                new String[] { SEARCHHISTORY_KEY },
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

        while (cursor.moveToNext()) {
            history.add(cursor.getString(0));
        }
        cursor.close();

        return history;
    }

    public synchronized void deleteMrlUri(String uri) {
        ArrayList<String> history = new ArrayList<String>();
        mDb.delete(MRL_TABLE_NAME, MRL_URI + "=?", new String[] { uri });

    }

    public synchronized void clearMrlHistory() {
        mDb.delete(MRL_TABLE_NAME, null, null);
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
                m.getLocation(),
                mediaColumn.MEDIA_PICTURE,
                p);
        } catch (SQLiteFullException e) {
            Log.d(TAG, "SQLiteFullException while setting picture");
        }
        m.setPictureParsed(true);
    }
}
