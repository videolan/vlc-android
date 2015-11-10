/*****************************************************************************
 * UiTools.java
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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.VLCApplication;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class Util {
    public final static String TAG = "VLC/Util";

    public static String readAsset(String assetName, String defaultS) {
        InputStream is = null;
        BufferedReader r = null;
        try {
            is = VLCApplication.getAppResources().getAssets().open(assetName);
            r = new BufferedReader(new InputStreamReader(is, "UTF8"));
            StringBuilder sb = new StringBuilder();
            String line = r.readLine();
            if(line != null) {
                sb.append(line);
                line = r.readLine();
                while(line != null) {
                    sb.append('\n');
                    sb.append(line);
                    line = r.readLine();
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return defaultS;
        } finally {
            close(is);
            close(r);
        }
    }

    public static String getPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = VLCApplication.getAppContext().getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            close(cursor);
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.GINGERBREAD)
    public static void commitPreferences(SharedPreferences.Editor editor){
        if (AndroidUtil.isGingerbreadOrLater())
            editor.apply();
        else
            editor.commit();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean deleteFile (String path){
        boolean deleted = false;
        path = Uri.decode(Strings.removeFileProtocole(path));
        //Delete from Android Medialib, for consistency with device MTP storing and other apps listing content:// media
        if (AndroidUtil.isHoneycombOrLater()){
            ContentResolver cr = VLCApplication.getAppContext().getContentResolver();
            String[] selectionArgs = { path };
            deleted = cr.delete(MediaStore.Files.getContentUri("external"),
                    MediaStore.Files.FileColumns.DATA + "=?", selectionArgs) > 0;
        }
        File file = new File(path);
        if (file.exists())
            deleted |= file.delete();
        return deleted;
    }

    public static boolean recursiveDelete(Context context, File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles())
                recursiveDelete(context, child);
            return fileOrDirectory.delete();
        } else {
            return deleteFile (fileOrDirectory.getPath());
        }
    }

    public static boolean close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean canWrite(String path){
        if (path == null)
            return false;
        if (path.startsWith("file://"))
            path = path.substring(7);
        if (!path.startsWith("/"))
            return false;
        if (path.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
            return true;
        if (AndroidUtil.isKitKatOrLater())
            return false;
        File file = new File(path);
        return (file.exists() && file.canWrite());
    }

    public static boolean isCallable(Intent intent) {
        List<ResolveInfo> list = VLCApplication.getAppContext().getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static String buildPkgString(String string) {
        return BuildConfig.APPLICATION_ID + "." + string;
    }
}
