/*****************************************************************************
 * BrowserAdapter.java
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

package org.videolan.vlc.gui;

import java.io.File;
import java.util.Comparator;
import java.util.List;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class BrowserAdapter extends ArrayAdapter<File>
                            implements Comparator<File> {
    public final static String TAG = "VLC/BrowserAdapter";

    public final static String ADD_ITEM_PATH = "/add/a/path";

    public BrowserAdapter(Context context) {
        super(context, 0);
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

        ViewHolder holder;
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.browser_item, parent, false);
            holder = new ViewHolder();
            holder.layout = view.findViewById(R.id.layout_item);
            holder.check = (CheckBox) view.findViewById(R.id.browser_item_selected);
            holder.text = (TextView) view.findViewById(R.id.browser_item_dir);
            view.setTag(holder);
        } else
            holder = (ViewHolder) view.getTag();

        final File item = getItem(position);
        final MediaDatabase dbManager = MediaDatabase.getInstance(view.getContext());

        if(item != null && item.getPath().equals(ADD_ITEM_PATH)) {
            holder.text.setText(R.string.add_custom_path);
            holder.check.setVisibility(View.GONE);
        } else if(item != null && item.getName() != null) {
            holder.text.setText(getVisibleName(item));
            holder.check.setVisibility(View.VISIBLE);
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setTag(item);
            holder.check.setEnabled(true);
            holder.check.setChecked(false);

            List<File> dirs = dbManager.getMediaDirs();
            for (File dir : dirs) {
                if (dir.getPath().equals(item.getPath())) {
                    holder.check.setEnabled(true);
                    holder.check.setChecked(true);
                    break;
                } else if (dir.getPath().startsWith(item.getPath()+"/")) {
                    Log.i(TAG, dir.getPath() + " startWith " + item.getPath());
                    holder.check.setEnabled(false);
                    holder.check.setChecked(true);
                    break;
                }
            }

            holder.check.setOnCheckedChangeListener(onCheckedChangeListener);
        }

        return view;
    }

    private final OnCheckedChangeListener onCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final MediaDatabase dbManager = MediaDatabase.getInstance(buttonView.getContext());
            File item = (File) buttonView.getTag();
            if (item == null)
                return;

            if (buttonView.isEnabled() && isChecked) {
                dbManager.addDir(item.getPath());
                File tmpFile = item.getParentFile();
                while (tmpFile != null && !tmpFile.getPath().equals("/")) {
                    dbManager.removeDir(tmpFile.getPath());
                    tmpFile = tmpFile.getParentFile();
                }
            } else {
                dbManager.removeDir(item.getPath());
            }
        }
    };

    public void sort() {
        super.sort(this);
    }

    @Override
    public int compare(File file1, File file2) {
        // float the add item to the bottom
        if(file1.getPath().equals(ADD_ITEM_PATH))
            return 1;
        else if(file2.getPath().equals(ADD_ITEM_PATH))
            return -1;

        return String.CASE_INSENSITIVE_ORDER.compare(file1.getName(), file2.getName());
    }

    private String getVisibleName(File file) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Show "sdcard" for the user's folder when running in multi-user
            if (file.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getPath())) {
                return VLCApplication.getAppContext().getString(R.string.internal_memory);
            }
        }
        return file.getName();
    }

    static class ViewHolder {
        View layout;
        CheckBox check;
        TextView text;
    }
}
