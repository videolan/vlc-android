package org.videolan.vlc.android;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

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
	private final int DB_VERSION = 3;
	
	private final String DIR_TABLE_NAME = "directories_table";
	private final String DIR_ROW_PATH = "path";
	
	private final String MEDIA_TABLE_NAME = "media_table";
	private final String MEDIA_PATH = "path";
	private final String MEDIA_TIME = "time";
	private final String MEDIA_LENGTH = "length";
	private final String MEDIA_TYPE = "type";
	private final String MEDIA_PICTURE = "picture";
	private final String MEDIA_TITLE = "title";
	private final String MEDIA_ARTIST = "artist";
	private final String MEDIA_GENRE = "genre";
	private final String MEDIA_ALBUM = "album";
	
	private final String SEARCHHISTORY_TABLE_NAME = "searchhistory_table";
	private final String SEARCHHISTORY_DATE = "date";
	private final String SEARCHHISTORY_KEY = "key";
	
	public enum mediaColumn { MEDIA_TABLE_NAME, MEDIA_PATH, MEDIA_TIME, MEDIA_LENGTH, 
		MEDIA_TYPE, MEDIA_PICTURE, MEDIA_TITLE, MEDIA_ARTIST, MEDIA_GENRE, MEDIA_ALBUM
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
	
	public synchronized static DatabaseManager getInstance() {
        if (instance == null) {
        	Context context = MainActivity.getInstance();
            instance = new DatabaseManager(context);
        }
        return instance;
    }
	
	
	private class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
				
			String createDirTabelQuery = "CREATE TABLE IF NOT EXISTS " 
				+ DIR_TABLE_NAME + " (" 
				+ DIR_ROW_PATH + " TEXT PRIMARY KEY NOT NULL" 
				+ ");"; 
			
			// Create the directories table
			db.execSQL(createDirTabelQuery);
			
			
			String createMediaTabelQuery = "CREATE TABLE IF NOT EXISTS " 
				+ MEDIA_TABLE_NAME + " (" 
				+ MEDIA_PATH + " TEXT PRIMARY KEY NOT NULL, " 		
				+ MEDIA_TIME + " INTEGER, "
				+ MEDIA_LENGTH + " INTEGER, "
				+ MEDIA_TYPE + " INTEGER, "
				+ MEDIA_PICTURE + " BLOB, "			
				+ MEDIA_TITLE + " VARCHAR(200), "
				+ MEDIA_ARTIST + " VARCHAR(200), "
				+ MEDIA_GENRE + " VARCHAR(200), "
				+ MEDIA_ALBUM + " VARCHAR(200)"
				+ ");"; 
			
			
			// Create the media table
			db.execSQL(createMediaTabelQuery);
			
			String createSearchhistoryTabelQuery = "CREATE TABLE IF NOT EXISTS " 
				+ SEARCHHISTORY_TABLE_NAME + " (" 
				+ SEARCHHISTORY_KEY + " VARCHAR(200) PRIMARY KEY NOT NULL, "
				+ SEARCHHISTORY_DATE + " DATETIME NOT NULL" 
				+ ");"; 
			
			// Create the searchhistory table
			db.execSQL(createSearchhistoryTabelQuery);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, 
				int newVersion) {
			// TODO ??
		}
	}
	
	
	/**
	 * Add a new media to the database. The picture can only added by update.
	 * @param meida which you like to add to the database
	 */
	public synchronized void addMedia(Media media) {
	
		ContentValues values = new ContentValues();
		
		values.put(MEDIA_PATH, media.getPath());
		values.put(MEDIA_TIME, media.getTime());
		values.put(MEDIA_LENGTH, media.getLength());
		values.put(MEDIA_TYPE, media.getType());
		values.put(MEDIA_TITLE, media.getTitle());
		values.put(MEDIA_ARTIST, media.getArtist());
		values.put(MEDIA_GENRE, media.getGenre());
		values.put(MEDIA_ALBUM, media.getAlbum());
		
		mDb.replace(MEDIA_TABLE_NAME, "NULL", values); 

	}
	
//	/**
//	 * Check if the item already in the database
//	 * @param path of the item (primary key)
//	 * @return 
//	 */
//	public synchronized boolean mediaItemExists(String path) {
//		Cursor cursor = mDb.query(MEDIA_TABLE_NAME, 
//				new String[] { DIR_ROW_PATH }, 
//				MEDIA_PATH + "=?", 
//				new String[] { path },
//				null, null, null);
//		boolean exists = cursor.moveToFirst();
//		cursor.close();
//		return exists;
//	}
	
	/**
	 * Get all paths from the items in the database
	 * @return list of File
	 */
	public synchronized List<File> getMediaFiles() {
		
		List<File> files = new ArrayList<File>();
		Cursor cursor;
		
		cursor = mDb.query(
				MEDIA_TABLE_NAME, 
				new String[] { MEDIA_PATH }, 
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
	

	public synchronized Media getMedia(String path) {
		
		Cursor cursor;
		Media media = null;
		Bitmap picture = null;
		byte[] blob;
		
		cursor = mDb.query(
				MEDIA_TABLE_NAME, 
				new String[] {  
						MEDIA_TIME,    //0 long
						MEDIA_LENGTH,  //1 long
						MEDIA_TYPE,    //2 int
						MEDIA_PICTURE, //3 Bitmap
						MEDIA_TITLE,   //4 string
						MEDIA_ARTIST,  //5 string
						MEDIA_GENRE,   //6 string
						MEDIA_ALBUM    //7 string
						}, 
				MEDIA_PATH + "=?", 
				new String[] { path },
				null, null, null);
		if (cursor.moveToFirst()) {
				
			blob = cursor.getBlob(3);
			if (blob != null) {
				picture = BitmapFactory.decodeByteArray(blob, 0, blob.length);
			}
			
			media = new Media(new File(path), cursor.getLong(0), 
					cursor.getLong(1), cursor.getInt(2), 
					picture, cursor.getString(4), 
					cursor.getString(5), cursor.getString(6), 
					cursor.getString(7));
		}

		return media;
	}
	
	public synchronized void removeMedia(String path) {
		mDb.delete(MEDIA_TABLE_NAME, MEDIA_PATH + "=?", new String[] { path });
	}
	
	public synchronized void updateMedia(String path, mediaColumn col, 
			Object object ) {
		ContentValues values = new ContentValues();
		switch (col) {
		case MEDIA_PICTURE:	
			Bitmap picture = (Bitmap)object;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			picture.compress(Bitmap.CompressFormat.PNG, 100, out);		
			values.put(MEDIA_PICTURE, out.toByteArray());
			break;
		default:
			return;
		}
		mDb.update(MEDIA_TABLE_NAME, values, MEDIA_PATH +"=?", new String[] { path });
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
		
		while(cursor.moveToNext()) {
			history.add(cursor.getString(0));
		}
		cursor.close();
		
		return history;
	}
	
	public synchronized void clearSearchhistory() {
		mDb.delete(SEARCHHISTORY_TABLE_NAME, null, null);
	}
	

}
