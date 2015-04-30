/*****************************************************************************
 * Util.java
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

package org.videolan.vlc.util;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.videolan.vlc.VLCApplication;

import java.util.ArrayList;
import java.util.Arrays;

public class CustomDirectories {

    public static void addCustomDirectory(String path) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());

        ArrayList<String> dirs = new ArrayList<String>(
                Arrays.asList(CustomDirectories.getCustomDirectories()));
        dirs.add(path);
        StringBuilder builder = new StringBuilder();
        builder.append(dirs.remove(0));
        for(String s : dirs) {
            builder.append(":");
            builder.append(s);
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("custom_paths", builder.toString());
        Util.commitPreferences(editor);
    }

    public static void removeCustomDirectory(String path) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        if(!preferences.getString("custom_paths", "").contains(path))
            return;
        ArrayList<String> dirs = new ArrayList<String>(
                Arrays.asList(preferences.getString("custom_paths", "").split(
                        ":")));
        dirs.remove(path);
        String custom_path;
        if(dirs.size() > 0) {
            StringBuilder builder = new StringBuilder();
            builder.append(dirs.remove(0));
            for(String s : dirs) {
                builder.append(":");
                builder.append(s);
            }
            custom_path = builder.toString();
        } else { // don't do unneeded extra work
            custom_path = "";
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("custom_paths", custom_path);
        Util.commitPreferences(editor);
    }

    public static String[] getCustomDirectories() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        final String custom_paths = preferences.getString("custom_paths", "");
        if(custom_paths.equals(""))
            return new String[0];
        else
            return custom_paths.split(":");
    }

    public static boolean contains(String directory){
        directory = directory.trim();
        String[] directories = getCustomDirectories();
        for (int i = 0 ; i < directories.length ; ++i){
            if (TextUtils.equals(directory, directories[i]))
                return true;
        }
        return false;
    }
}
