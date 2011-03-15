package vlc.android;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class SimpleFileBrowser extends ListActivity {

    /**
     * The simplest file browser ever, inspired by:
     * http://android-er.blogspot.com/2010/01/implement-simple-file-explorer-in.html
     */

    private static final String TAG = "LibVLC/SimpleFileBrowser";

    private static final String ROOT_FOLDER = "/";
    
    private TextView mPathWidget;
    
    private Bitmap dirImage;
    
    public FileBrowserAdapter mItems;
    
    private ThumbnailerManager mThumbnailerManager;
    public int mItemIdToUpdate;
    public FileBrowserItem mNewItem;
    
    // Need handler for callbacks to the UI thread
    final Handler mHandler = new Handler();
    final CyclicBarrier mBarrier = new CyclicBarrier(2);

    // Counter to determine if an updated item is no longer displayed or not.
    private int mCount;
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "Opening file browser");
        
        InputStream is = this.getResources().openRawResource(R.raw.dir);
        try {
            dirImage = BitmapFactory.decodeStream(is);
        } finally {
            try {
                is.close();
            } catch(IOException e) {
                // Ignore.
            }
        }

        /*
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(VlcPreferences.ORIENTATION_MODE, true)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        */

        /* Set display layout */
        setContentView(R.layout.filebrowser);
        mPathWidget = (TextView) findViewById(R.id.filebrowser_path);

        /* Firstly, let's check the SD card state */
        boolean mExternalStorageAvailable = false;
        final String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStorageAvailable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStorageAvailable = true;
        }

        // FIXME Support internal storage as well
        if (!mExternalStorageAvailable) {
            Util.toaster(R.string.filebrowser_sdfail);
            finish();
            return;
        }

        /* Create and set the list activity array adapter. */
        mItems = new FileBrowserAdapter(this, R.layout.filebrowser_item);
        setListAdapter(mItems);
        
        /* Prepare the thumbnailer after having created mItems. */
        mThumbnailerManager = new ThumbnailerManager(this);
        
        openDir(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    /**
     * Open a directory and prepare the item to display and
     * the thumbnails to generate.
     * @param path the directory path.
     */
    private void openDir(String path) {
        // FIXME Display only folder name
        mPathWidget.setText(path);

        mItems.clear();
        mThumbnailerManager.clearJobs();
        
        mCount++;

        File dir = new File(path);
        File[] files = dir.listFiles(); // FIXME Use a filter

        if (!ROOT_FOLDER.equals(path)) {
            FileBrowserItem item =
                new FileBrowserItem(getString(R.string.filebrowser_root),
                    ROOT_FOLDER, dirImage, mCount++);
            mItems.add(item);
            
            item = new FileBrowserItem(getString(R.string.filebrowser_parent),
                    dir.getParent(), dirImage, mCount);
            mItems.add(item);
        }

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f.getName().startsWith("."))
                    continue;

                Log.v(TAG, "File found: " + f.getName());
                if (f.isDirectory()) {
                    FileBrowserItem item =
                        new FileBrowserItem(f.getName() + "/",
                            f.getAbsolutePath(), dirImage, mCount);
                    mItems.add(item);
                }
                else {
                    FileBrowserItem item = new FileBrowserItem(f.getName(),
                            f.getAbsolutePath(), null, mCount);
                    mItems.add(item);
                    // FIXME the file extensions.
                    if (f.getName().endsWith(".mp4")
                        || f.getName().endsWith(".avi"))
                        mThumbnailerManager.addJob(mItems.getCount() - 1);
                }
            }
        } else {
            Log.v(TAG, "No files in this folder");
        }
    }

    /**
     * The item click callback function.
     */
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        String path = mItems.getItem((int)id).path;
        File f = new File(path);
        if (f.isDirectory()) {
            openDir(path);
        }
        else {
            // Interrupt the thumbnailer thread.
            mThumbnailerManager.interrupt();
            Log.v(TAG, "Play: " + path);
            Bundle bundle = new Bundle();
            bundle.putString("filePath", path);
            Intent i = new Intent();
            i.putExtras(bundle);
            setResult(RESULT_OK, i);
            finish();
        }
    }
    
    /**
     * Runable to update an file browser item after its thumbnail generation.
     */
    public final Runnable mUpdateItems = new Runnable() {
        public void run() {
            // Update the item only if it is still displayed.
            if (mNewItem.count == mCount)
            {
                mItems.remove(mItems.getItem(mItemIdToUpdate));
                mItems.insert(mNewItem, mItemIdToUpdate);
            }
            try {
                mBarrier.await();
            } catch (InterruptedException e) {
            } catch (BrokenBarrierException e) {
            }
        }
    };
    
    /**
     * A file browser item.
     */
    public class FileBrowserItem {
        public String name;
        public String path;
        public Bitmap thumbnail;
        public int count;
        
        public FileBrowserItem(String name, String path,
                Bitmap thumbnail, int count) {
            this.name = name;
            this.path = path;
            this.thumbnail = thumbnail;
            this.count = count;
        }
    }


    public class FileBrowserAdapter extends ArrayAdapter<FileBrowserItem> {

        public synchronized void add(FileBrowserItem object) {
            super.add(object);
        }
        
        public synchronized FileBrowserItem getItem(int position) {
            return super.getItem(position);
        }
        
        public synchronized void remove(FileBrowserItem object) {
            super.remove(object);
        }
        
        public synchronized void insert(FileBrowserItem object, int index) {
            super.insert(object, index);
        }
        
        public FileBrowserAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            }
        
        /**
         * Display the view of a file browser item.
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.filebrowser_item, null);
            }

            FileBrowserItem item = getItem(position);
            if (item != null) {
                TextView textView = (TextView)v.findViewById(R.id.text);
                ImageView imageView = (ImageView)v.findViewById(R.id.image);

                textView.setText(item.name);
                imageView.setImageBitmap(item.thumbnail);
            }

            return v;
        }
    }
    
    // TODO Finish this work
}
