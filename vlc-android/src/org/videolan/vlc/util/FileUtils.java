/*
 * *************************************************************************
 *  FileUtils.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.util;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.VLCApplication;

import java.io.File;

public class FileUtils {

    public interface Callback {
        void onResult(boolean success);
    }

    public static String getFileNameFromPath(String path){
        if (path == null)
            return "";
        int index = path.lastIndexOf('/');
        if (index> -1)
            return path.substring(index+1);
        else
            return path;
    }

    public static String getParent(String path){
        if (TextUtils.equals("/", path))
            return path;
        String parentPath = path;
        if (parentPath.endsWith("/"))
            parentPath = parentPath.substring(0, parentPath.length()-1);
        int index = parentPath.lastIndexOf('/');
        if (index > 0){
            parentPath = parentPath.substring(0, index);
        } else if (index == 0)
            parentPath = "/";
        return parentPath;
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
            Util.close(cursor);
        }
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

    public static void asyncRecursiveDelete(String path, Callback callback) {
        asyncRecursiveDelete(new File(path), callback);
    }

    public static void asyncRecursiveDelete(String path) {
        asyncRecursiveDelete(path, null);
    }

    private static void asyncRecursiveDelete(final File fileOrDirectory, final Callback callback) {
        VLCApplication.runBackground(new Runnable() {
            public void run() {
                if (!fileOrDirectory.exists() || !fileOrDirectory.canWrite())
                    return;
                boolean success = true;
                if (fileOrDirectory.isDirectory()) {
                    for (File child : fileOrDirectory.listFiles())
                        asyncRecursiveDelete(child, null);
                    success = fileOrDirectory.delete();
                } else {
                    success = deleteFile(fileOrDirectory.getPath());
                }
                if (callback != null)
                    callback.onResult(success);
            }
        });
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
}
