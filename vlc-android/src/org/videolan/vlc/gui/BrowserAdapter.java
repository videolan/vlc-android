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

import org.videolan.vlc.DatabaseManager;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.content.Context;
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
            holder.layout = (View) view.findViewById(R.id.layout_item);
            holder.check = (CheckBox) view.findViewById(R.id.browser_item_selected);
            holder.text = (TextView) view.findViewById(R.id.browser_item_dir);
            view.setTag(holder);
        } else
            holder = (ViewHolder) view.getTag();

        final File item = getItem(position);
        final DatabaseManager dbManager = DatabaseManager.getInstance(view.getContext());

        if (item != null && item.getName() != null) {
            Util.setItemBackground(holder.layout, position);
            holder.text.setText(item.getName());
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

    private OnCheckedChangeListener onCheckedChangeListener = new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final DatabaseManager dbManager = DatabaseManager.getInstance(buttonView.getContext());
            File item = (File) buttonView.getTag();
            if (item == null)
                return;

            if (buttonView.isEnabled() && isChecked) {
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
    };

    public void sort() {
        super.sort(this);
    }

    public int compare(File file1, File file2) {
        return file1.getName().toUpperCase().compareTo(
                file2.getName().toUpperCase());
    }

    static class ViewHolder {
        View layout;
        CheckBox check;
        TextView text;
    }
}
