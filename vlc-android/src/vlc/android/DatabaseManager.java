package vlc.android;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseManager {
	public final static String TAG = "VLC/DatabaseManager";
	
	private static DatabaseManager instance;

	private SQLiteDatabase db;
	private final String DB_NAME = "vlc_database";
	private final int DB_VERSION = 1;
	
	private final String DIR_TABLE_NAME = "directories_table";
	private final String DIR_ROW_PATH = "path";
	
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
		this.db = helper.getWritableDatabase();
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
				
			String createTabelQuery = 
				"CREATE TABLE " + DIR_TABLE_NAME + " (" +
				DIR_ROW_PATH + " TEXT PRIMARY KEY NOT NULL);"; 
			
			// Create the directories table
			db.execSQL(createTabelQuery);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO ??
		}
		
	}
	
	
	/** 
	 * Add directory to the directories table
	 * 
	 * @param path
	 */
	public synchronized void addMediaDir(String path) {
		ContentValues values = new ContentValues();
		values.put(DIR_ROW_PATH, path);
		try {
			db.insertOrThrow(DIR_TABLE_NAME, null, values); // FIXME: Exception if already exists
		} catch (SQLException e) {
			Log.w(TAG, "Directory (" + path + ") already in database");
		}
	}
	
	/**
	 * Delete directory from directories table
	 * 
	 * @param path
	 */
	public synchronized void removeMediaDir(String path) {
		db.delete(DIR_TABLE_NAME, DIR_ROW_PATH + "='" + path + "'", null);
	}

	/**
	 * 
	 * @return
	 */
	public synchronized List<File> getMediaDirs() {
		
		List<File> paths = new ArrayList<File>();
		Cursor cursor;
		
		cursor = db.query(
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
		Cursor cursor = db.query(DIR_TABLE_NAME, 
				new String[] { DIR_ROW_PATH }, 
				DIR_ROW_PATH + "='" + path + "'", 
				null, null, null, null);
		Log.i(TAG, path + " = null? " + cursor.moveToFirst());
		cursor.close();
		return cursor.moveToFirst();
	}
	

}
