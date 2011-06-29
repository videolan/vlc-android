package org.videolan.vlc.android;


import java.io.ByteArrayOutputStream;
import java.io.File;
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
	private final int DB_VERSION = 2;
	
	private final String DIR_TABLE_NAME = "directories_table";
	private final String DIR_ROW_PATH = "path";
	
	private final String MEDIA_TABLE_NAME = "media_table";
	private final String MEDIA_NAME = "name";
	private final String MEDIA_PATH = "path";
	private final String MEDIA_TIME = "time";
	private final String MEDIA_LENGTH = "length";
	private final String MEDIA_TYPE = "type";
	private final String MEDIA_WIDTH = "width";
	private final String MEDIA_HEIGHT = "height";
	private final String MEDIA_THUMBNAIL = "thumbnail";
	
	public enum mediaColumn { MEDIA_NAME, MEDIA_PATH, MEDIA_TIME, MEDIA_LENGTH,
		MEDIA_TYPE, MEDIA_WIDTH, MEDIA_HEIGHT, MEDIA_THUMBNAIL
	}
	
	// TODO: Create database table for items
//	private final String ITEM_TABLE_NAME = "item_table";
//	private final String ITEM_ROW_ID = "id";
	
	
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
        	Context context = MediaLibraryActivity.getInstance();
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
				+ MEDIA_NAME + " VARCHAR(200) NOT NULL, "
				+ MEDIA_PATH + " TEXT PRIMARY KEY NOT NULL, " 
				+ MEDIA_TIME + " INTEGER, "
				+ MEDIA_LENGTH + " INTEGER, "
				+ MEDIA_TYPE + " INTEGER, "
				+ MEDIA_WIDTH + " INTEGER, "
				+ MEDIA_HEIGHT + " INTEGER, "
				+ MEDIA_THUMBNAIL + " BLOB"
				+ ");"; 
			
			// Create the media table
			db.execSQL(createMediaTabelQuery);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, 
				int newVersion) {
			// TODO ??
		}
	}
	
	
	/**
	 * Add a new item to the database. The thumbnail can only added by update.
	 * @param item which you like to add to the database
	 */
	public synchronized void addMediaItem(MediaItem item) {
		if (!mediaDirExists(item.getPath())) {
			
			ContentValues values = new ContentValues();
			values.put(MEDIA_NAME, item.getName());
			values.put(MEDIA_PATH, item.getPath());
			values.put(MEDIA_TIME, item.getTime());
			values.put(MEDIA_LENGTH, item.getLength());
			values.put(MEDIA_TYPE, item.getType());
			values.put(MEDIA_WIDTH, item.getWidth());
			values.put(MEDIA_HEIGHT, item.getHeight());
			
			mDb.insert(MEDIA_TABLE_NAME, null, values); 
		}
	}
	
	/**
	 * Check if the item already in the database
	 * @param path of the item (primary key)
	 * @return 
	 */
	public synchronized boolean mediaItemExists(String path) {
		Cursor cursor = mDb.query(MEDIA_TABLE_NAME, 
				new String[] { DIR_ROW_PATH }, 
				MEDIA_PATH + "=?", 
				new String[] { path },
				null, null, null);
		boolean exists = cursor.moveToFirst();
		cursor.close();
		return exists;
	}
	
	/**
	 * Get all paths from the items in the database
	 * @return list of File
	 */
	public synchronized List<File> getMediaItemPaths() {
		
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
	

	public synchronized MediaItem getMediaItem(String path) {
		
		Cursor cursor;
		MediaItem item = null;
		Bitmap thumbnail = null;
		byte[] blob;
		
		cursor = mDb.query(
				MEDIA_TABLE_NAME, 
				new String[] {  
						MEDIA_NAME,    //0 String
						MEDIA_PATH,    //1 String 
						MEDIA_TIME,    //2 long
						MEDIA_LENGTH,  //3 long
						MEDIA_TYPE,    //4 int
						MEDIA_WIDTH,   //5 int
						MEDIA_HEIGHT,  //6 int
						MEDIA_THUMBNAIL//7 Bitmap
						}, 
				MEDIA_PATH + "=?", 
				new String[] { path },
				null, null, null);
		if (cursor.moveToFirst()) {
				
			blob = cursor.getBlob(7);
			if (blob != null) {
				thumbnail = BitmapFactory.decodeByteArray(blob, 0, blob.length);
			}
			
			item = new MediaItem(
					cursor.getString(0),
					new File(cursor.getString(1)),
					cursor.getLong(2),
					cursor.getLong(3),
					cursor.getInt(4),
					cursor.getInt(5),
					cursor.getInt(6),
					thumbnail
					);
		}

		return item;
	}
	
	public synchronized void removeMediaItem(String path) {
		mDb.delete(MEDIA_TABLE_NAME, MEDIA_PATH + "=?", new String[] { path });
	}
	
	public synchronized void updateMediaItem(String path, mediaColumn col, 
			Object object ) {
		ContentValues values = new ContentValues();
		switch (col) {
		case MEDIA_THUMBNAIL:	
			Bitmap thumbnail = (Bitmap)object;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			thumbnail.compress(Bitmap.CompressFormat.PNG, 100, out);		
			values.put(MEDIA_THUMBNAIL, out.toByteArray());
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
	public synchronized void addMediaDir(String path) {	
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
	public synchronized void removeMediaDir(String path) {
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
	

}
