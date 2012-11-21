/*****************************************************************************
 * DatabaseManager.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class DatabaseManager {
    public final static String TAG = "VLC/DatabaseManager";

    private static DatabaseManager instance;

    private SQLiteDatabase mDb;
    private final String DB_NAME = "vlc_database";
    private final int DB_VERSION = 7;
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
    private final String MEDIA_WIDTH = "width";
    private final String MEDIA_HEIGHT = "height";
    private final String MEDIA_ARTWORKURL = "artwork_url";

    private final String PLAYLIST_TABLE_NAME = "playlist_table";
    private final String PLAYLIST_NAME = "name";

    private final String PLAYLIST_MEDIA_TABLE_NAME = "playlist_media_table";
    private final String PLAYLIST_MEDIA_ID = "id";
    private final String PLAYLIST_MEDIA_PLAYLISTNAME = "playlist_name";
    private final String PLAYLIST_MEDIA_MEDIAPATH = "media_path";

    private final String SEARCHHISTORY_TABLE_NAME = "searchhistory_table";
    private final String SEARCHHISTORY_DATE = "date";
    private final String SEARCHHISTORY_KEY = "key";

    public enum mediaColumn {
        MEDIA_TABLE_NAME, MEDIA_PATH, MEDIA_TIME, MEDIA_LENGTH,
        MEDIA_TYPE, MEDIA_PICTURE, MEDIA_TITLE, MEDIA_ARTIST, MEDIA_GENRE, MEDIA_ALBUM,
        MEDIA_WIDTH, MEDIA_HEIGHT, MEDIA_ARTWORKURL
    }

    /**
     * Constructor
     *
     * @param context
     */
    private DatabaseManager(Context context) {
        // create or open database
        DatabaseHelper helper = new DatabaseHelper(context);
        this.mDb = helper.getWritableDatabase();
    }

    public synchronized static DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        public void dropMediaTabelQuery(SQLiteDatabase db) {
            String query = "DROP TABLE " + MEDIA_TABLE_NAME + ";";
            db.execSQL(query);
        }

        public void createMediaTabelQuery(SQLiteDatabase db) {
            String query = "CREATE TABLE IF NOT EXISTS "
                    + MEDIA_TABLE_NAME + " ("
                    + MEDIA_LOCATION + " TEXT PRIMARY KEY NOT NULL, "
                    + MEDIA_TIME + " INTEGER, "
                    + MEDIA_LENGTH + " INTEGER, "
                    + MEDIA_TYPE + " INTEGER, "
                    + MEDIA_PICTURE + " BLOB, "
                    + MEDIA_TITLE + " VARCHAR(200), "
                    + MEDIA_ARTIST + " VARCHAR(200), "
                    + MEDIA_GENRE + " VARCHAR(200), "
                    + MEDIA_ALBUM + " VARCHAR(200), "
                    + MEDIA_WIDTH + " INTEGER, "
                    + MEDIA_HEIGHT + " INTEGER, "
                    + MEDIA_ARTWORKURL + " VARCHAR(256)"
                    + ");";
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
            createMediaTabelQuery(db);

            String createPlaylistTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    PLAYLIST_TABLE_NAME + " (" +
                    PLAYLIST_NAME + " VARCHAR(200) PRIMARY KEY NOT NULL);";

            db.execSQL(createPlaylistTableQuery);

            String createPlaylistMediaTableQuery = "CREATE TABLE IF NOT EXISTS " +
                    PLAYLIST_MEDIA_TABLE_NAME + " (" +
                    PLAYLIST_MEDIA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    PLAYLIST_MEDIA_PLAYLISTNAME + " VARCHAR(200) NOT NULL," +
                    PLAYLIST_MEDIA_MEDIAPATH + " TEXT NOT NULL);";

            db.execSQL(createPlaylistMediaTableQuery);

            String createSearchhistoryTabelQuery = "CREATE TABLE IF NOT EXISTS "
                    + SEARCHHISTORY_TABLE_NAME + " ("
                    + SEARCHHISTORY_KEY + " VARCHAR(200) PRIMARY KEY NOT NULL, "
                    + SEARCHHISTORY_DATE + " DATETIME NOT NULL"
                    + ");";

            // Create the searchhistory table
            db.execSQL(createSearchhistoryTabelQuery);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 7 && newVersion == 7) {
                dropMediaTabelQuery(db);
                createMediaTabelQuery(db);
            }
        }
    }

    /**
     * Get all playlists in the database
     * @return
     */
    public String[] getPlaylists() {
        ArrayList<String> playlists = new ArrayList<String>();
        Cursor cursor;

        cursor = mDb.query(
                PLAYLIST_TABLE_NAME,
                new String[] { PLAYLIST_NAME },
                null, null, null, null, null);
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            do {
                playlists.add(cursor.getString(10));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return (String[]) playlists.toArray();
    }

    /**
     * Add new playlist
     * @param name
     * @return id of the new playlist
     */
    public void addPlaylist(String name) {
        ContentValues values = new ContentValues();
        values.put(PLAYLIST_NAME, name);
        mDb.insert(PLAYLIST_TABLE_NAME, "NULL", values);
    }

    public void deletePlaylist(String name) {
        mDb.delete(PLAYLIST_TABLE_NAME, PLAYLIST_NAME + "=?",
                new String[] { name });
    }

    public void addMediaToPlaylist(String playlistName, String mediaPath) {
        ContentValues values = new ContentValues();
        values.put(PLAYLIST_MEDIA_PLAYLISTNAME, playlistName);
        values.put(PLAYLIST_MEDIA_MEDIAPATH, mediaPath);
    }

    public void removeMediaFromPlaylist(String playlistName, String mediaPath) {
        mDb.delete(PLAYLIST_MEDIA_TABLE_NAME,
                PLAYLIST_MEDIA_PLAYLISTNAME + "=? "
                        + PLAYLIST_MEDIA_MEDIAPATH + "=?",
                new String[] { playlistName, mediaPath });
    }

    /**
     * Add a new media to the database. The picture can only added by update.
     * @param meida which you like to add to the database
     */
    public synchronized void addMedia(Media media) {

        ContentValues values = new ContentValues();

        values.put(MEDIA_LOCATION, media.getLocation());
        values.put(MEDIA_TIME, media.getTime());
        values.put(MEDIA_LENGTH, media.getLength());
        values.put(MEDIA_TYPE, media.getType());
        values.put(MEDIA_TITLE, media.getTitle());
        values.put(MEDIA_ARTIST, media.getArtist());
        values.put(MEDIA_GENRE, media.getGenre());
        values.put(MEDIA_ALBUM, media.getAlbum());
        values.put(MEDIA_WIDTH, media.getWidth());
        values.put(MEDIA_HEIGHT, media.getHeight());
        values.put(MEDIA_ARTWORKURL, media.getArtworkURL());

        mDb.replace(MEDIA_TABLE_NAME, "NULL", values);

    }

    //    /**
    //     * Check if the item already in the database
    //     * @param path of the item (primary key)
    //     * @return
    //     */
    //    public synchronized boolean mediaItemExists(String path) {
    //        Cursor cursor = mDb.query(MEDIA_TABLE_NAME,
    //                    new String[] { DIR_ROW_PATH },
    //                    MEDIA_PATH + "=?",
    //                    new String[] { path },
    //                    null, null, null);
    //        boolean exists = cursor.moveToFirst();
    //        cursor.close();
    //        return exists;
    //    }

    /**
     * Get all paths from the items in the database
     * @return list of File
     */
    public synchronized HashSet<File> getMediaFiles() {

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

    public synchronized HashMap<String, Media> getMedias(Context context) {

        Cursor cursor;
        HashMap<String, Media> medias = new HashMap<String, Media>();
        int chunk_count = 0;
        int count = 0;

        do {
            count = 0;
            cursor = mDb.rawQuery(String.format(Locale.US,
                    "SELECT %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s FROM %s LIMIT %d OFFSET %d",
                    MEDIA_TIME, //0 long
                    MEDIA_LENGTH, //1 long
                    MEDIA_TYPE, //2 int
                    MEDIA_TITLE, //3 string
                    MEDIA_ARTIST, //4 string
                    MEDIA_GENRE, //5 string
                    MEDIA_ALBUM, //6 string
                    MEDIA_WIDTH, //7 int
                    MEDIA_HEIGHT, //8 int
                    MEDIA_ARTWORKURL, //9 string
                    MEDIA_LOCATION, //10 string
                    MEDIA_TABLE_NAME,
                    CHUNK_SIZE,
                    chunk_count * CHUNK_SIZE), null);

            if (cursor.moveToFirst()) {
                do {
                    String location = cursor.getString(10);
                    Media media = new Media(context, location,
                            cursor.getLong(0),      // MEDIA_PICTURE
                            cursor.getLong(1),      // MEDIA_LENGTH
                            cursor.getInt(2),       // MEDIA_TYPE
                            null,                   // MEDIA_PICTURE
                            cursor.getString(3),    // MEDIA_TITLE
                            cursor.getString(4),    // MEDIA_ARTIST
                            cursor.getString(5),    // MEDIA_GENRE
                            cursor.getString(6),    // MEDIA_ALBUM
                            cursor.getInt(7),       // MEDIA_WIDTH
                            cursor.getInt(8),       // MEDIA_HEIGHT
                            cursor.getString(9));   // MEDIA_ARTWORKURL
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
                    Media.TYPE_VIDEO,
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

    public synchronized Media getMedia(Context context, String location) {

        Cursor cursor;
        Media media = null;

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
                        MEDIA_WIDTH, //7 int
                        MEDIA_HEIGHT, //8 int
                        MEDIA_ARTWORKURL //9 string
                },
                MEDIA_LOCATION + "=?",
                new String[] { location },
                null, null, null);
        if (cursor.moveToFirst()) {
            media = new Media(context, location,
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getInt(2),
                    null,
                    cursor.getString(3),
                    cursor.getString(4),
                    cursor.getString(5),
                    cursor.getString(6),
                    cursor.getInt(7),
                    cursor.getInt(8),
                    cursor.getString(9));
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
                picture = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                blob = null;
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
     * Delete directory from directories table if not under root folder
     *
     * @param path
     * @deprecated Since we have now multiple root directories, this method is now deprecated.
     */
    public synchronized void removeDirNotUnder(String root) {
        mDb.delete(DIR_TABLE_NAME, DIR_ROW_PATH + " NOT LIKE ?", new String[] { root+"%" });
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

    public synchronized boolean mediaDirExists(String path) {
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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

    public synchronized void clearSearchhistory() {
        mDb.delete(SEARCHHISTORY_TABLE_NAME, null, null);
    }

    /**
     * Empty the database for debugging purposes
     */
    public synchronized void emptyDatabase() {
        mDb.delete(MEDIA_TABLE_NAME, null, null);
    }
}
