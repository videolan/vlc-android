package vlc.android;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class SimpleFileBrowser extends ListActivity {

    /**
     * The simplest file browser ever, inspired by:
     * http://android-er.blogspot.com/2010/01/implement-simple-file-explorer-in.html
     */

    private static final String TAG = "LibVLC/SimpleFileBrowser";

    private static final String ROOT_FOLDER = "/";

    private List<String> mFiles;
    private List<String> mPaths;
    private TextView mPathWidget;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "Opening file browser");

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

        openDir(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    private void openDir(String path) {
        // FIXME Display only folder name
        mPathWidget.setText(path);

        mFiles = new ArrayList<String>();
        mPaths = new ArrayList<String>();

        File dir = new File(path);
        File[] files = dir.listFiles(); // FIXME Use a filter

        if (!ROOT_FOLDER.equals(path)) {
            mFiles.add(getString(R.string.filebrowser_root));
            mPaths.add(ROOT_FOLDER);

            mFiles.add(getString(R.string.filebrowser_parent));
            mPaths.add(dir.getParent());
        }

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                Log.v(TAG, "File found: " + f.getName());
                mFiles.add(f.getName() + (f.isDirectory() ? "/" : ""));
                mPaths.add(f.getAbsolutePath());
            }
        } else {
            Log.v(TAG, "No files in this folder");
        }

        ArrayAdapter<String> fileList =
            new ArrayAdapter<String>(this, R.layout.filebrowser_item, mFiles);
        setListAdapter(fileList);
    }

    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        String path = mPaths.get((int)id);
        File f = new File(path);
        if (f.isDirectory()) {
            openDir(path);
        }
        else {
            Log.v(TAG, "Play: " + path);
        }
    }
    
    // TODO Finish this work
}
