package org.videolan.vlc.android;

import java.io.File;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class BrowserAdapter extends ArrayAdapter<File>
                            implements Comparator<File> {
    public final static String TAG = "VLC/BrowserAdapter";

    public BrowserAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    @Override
    public synchronized void add(File object) {
        super.add(object);
    }

    /**
     * Display the view of a file browser item.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) this.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.browser_item,
                    parent, false);
        }

        final File item = getItem(position);
        final DatabaseManager dbManager = DatabaseManager.getInstance();

        if (item != null && item.getName() != null) {
            TextView dirTextView =
                    (TextView) view.findViewById(R.id.browser_item_dir);
            dirTextView.setText(item.getName());
            final CheckBox dirCheckBox =
                    (CheckBox) view.findViewById(R.id.browser_item_selected);

            dirCheckBox.setOnCheckedChangeListener(null);

            dirCheckBox.setEnabled(true);
            dirCheckBox.setChecked(false);

            List<File> dirs = dbManager.getMediaDirs();
            for (File dir : dirs) {
                if (dir.getPath().equals(item.getPath())) {
                    dirCheckBox.setEnabled(true);
                    dirCheckBox.setChecked(true);
                    break;
                } else if (dir.getPath().startsWith(item.getPath())) {
                    Log.i(TAG, item.getPath() + " startWith " + dir.getPath());
                    dirCheckBox.setEnabled(false);
                    dirCheckBox.setChecked(true);
                    break;
                }
            }

            dirCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView,
                        boolean isChecked) {
                    if (dirCheckBox.isEnabled() && isChecked) {
                        dbManager.addDir(item.getPath());
                        File tmpFile = item;
                        while (!tmpFile.getPath().equals("/")) {
                            tmpFile = tmpFile.getParentFile();
                            dbManager.removeDir(tmpFile.getPath());
                        }
                    } else {
                        dbManager.removeDir(item.getPath());
                    }
                }
            });
        }

        return view;
    }

    public void sort() {
        super.sort(this);
    }

    public int compare(File file1, File file2) {
        return file1.getName().toUpperCase().compareTo(
                file2.getName().toUpperCase());
    }

}
