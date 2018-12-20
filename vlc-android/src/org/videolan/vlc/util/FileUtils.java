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

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.media.MediaUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

public class FileUtils {

    public final static String TAG = "VLC/FileUtils";

    /**
     * Size of the chunks that will be hashed in bytes (64 KB)
     */
    private static final int HASH_CHUNK_SIZE = 64 * 1024;

    public interface Callback {
        void onResult(boolean success);
    }

    public static String getFileNameFromPath(String path){
        if (path == null)
            return "";
        int index = path.lastIndexOf('/');
        if (index == path.length()-1) {
            path = path.substring(0, index);
            index = path.lastIndexOf('/');
        }
        if (index > -1)
            return path.substring(index+1);
        else
            return path;
    }

    public static String getParent(String path){
        if (path == null || TextUtils.equals("/", path))
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

    /*
     * Convert file:// uri from real path to emulated FS path.
     */
    public static Uri convertLocalUri(Uri uri) {
        if (!TextUtils.equals(uri.getScheme(), "file") || !uri.getPath().startsWith("/sdcard"))
            return uri;
        return Uri.parse(uri.toString().replace("/sdcard", AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY));
    }

    @WorkerThread
    public static String getPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            final String[] proj = {MediaStore.Images.Media.DATA};
            cursor = VLCApplication.getAppContext().getContentResolver().query(contentUri, proj, null, null, null);
            if (cursor == null || cursor.getCount() == 0)
                return "";
            final int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (IllegalArgumentException|SecurityException e) {
                return "";
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
    }

    public static void copyLua(final Context context, final boolean force) {
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final String destinationFolder = context.getDir("vlc",
                        Context.MODE_PRIVATE).getAbsolutePath() + "/.share/lua";
                final AssetManager am = VLCApplication.getAppResources().getAssets();
                copyAssetFolder(am, "lua", destinationFolder, force);
            }
        });
    }

    @WorkerThread
    static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath, boolean force) {
        try {
            final String[] files = assetManager.list(fromAssetPath);
            if (files.length == 0) return false;
            new File(toPath).mkdirs();
            boolean res = true;
            for (String file : files)
                if (file.contains("."))
                    res &= copyAsset(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file,
                            force);
                else
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file,
                            force);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @WorkerThread
    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath, boolean force) {
        final File destFile = new File(toPath);
        if (!force && destFile.exists()) return true;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            destFile.createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            out.flush();
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            Util.close(in);
            Util.close(out);
        }
    }

    @WorkerThread
    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
          out.write(buffer, 0, read);
        }
    }

    @WorkerThread
    public static boolean copyFile(File src, File dst){
        boolean ret = true;
        if (src.isDirectory()) {
            File[] filesList = src.listFiles();
            dst.mkdirs();
            for (File file : filesList)
                ret &= copyFile(file, new File(dst, file.getName()));
        } else if (src.isFile()) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new BufferedInputStream(new FileInputStream(src));
                out = new BufferedOutputStream(new FileOutputStream(dst));

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return true;
            } catch (IOException ignored) {
            } finally {
                Util.close(in);
                Util.close(out);
            }
            return false;
        }
        return ret;
    }

    @WorkerThread
    public static boolean deleteFile (Uri uri) {
        if (!AndroidUtil.isLolliPopOrLater || uri.getPath().startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) return deleteFile(uri.getPath());
        final DocumentFile docFile = FileUtils.findFile(uri);
        if (docFile != null) try {
            return docFile.delete();
        } catch (Exception ignored) {}
        return false;
    }

    @WorkerThread
    public static boolean deleteFile (String path) {
        return deleteFile(new File(path));
    }

    @WorkerThread
    public static boolean deleteFile (File file){
        boolean deleted;
        //Delete from Android Medialib, for consistency with device MTP storing and other apps listing content:// media
        if (file.isDirectory()) {
            deleted = true;
            for (File child : file.listFiles()) deleted &= deleteFile(child);
            if (deleted) deleted &= file.delete();
        } else {
            final ContentResolver cr = VLCApplication.getAppContext().getContentResolver();
            try {
                deleted = cr.delete(MediaStore.Files.getContentUri("external"),
                        MediaStore.Files.FileColumns.DATA + "=?", new String[]{file.getPath()}) > 0;
            } catch (IllegalArgumentException | SecurityException ignored) {
                deleted = false;
            } // Can happen on some devices...
            if (file.exists()) deleted |= file.delete();
        }
        return deleted;
    }

    private static void asyncRecursiveDelete(String path, Callback callback) {
        asyncRecursiveDelete(new File(path), callback);
    }

    public static void asyncRecursiveDelete(String path) {
        asyncRecursiveDelete(path, null);
    }

    private static void asyncRecursiveDelete(final File fileOrDirectory, final Callback callback) {
        WorkersKt.runIO(new Runnable() {
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

    public static boolean canSave(MediaWrapper mw) {
        if (mw == null || mw.getUri() == null) return false;
        final String scheme = mw.getUri().getScheme();
        return (TextUtils.equals(scheme, "file") || TextUtils.equals(scheme, "smb")
                        || TextUtils.equals(scheme, "nfs") || TextUtils.equals(scheme, "ftp")
                        || TextUtils.equals(scheme, "ftps") || TextUtils.equals(scheme, "sftp"));
    }

    @WorkerThread
    public static boolean canWrite(Uri uri) {
        if (uri == null) return false;
        if (TextUtils.equals("file", uri.getScheme()))
            return canWrite(uri.getPath());
        return TextUtils.equals("content", uri.getScheme()) && canWrite(getPathFromURI(uri));
    }

    @WorkerThread
    public static boolean canWrite(String path){
        if (TextUtils.isEmpty(path)) return false;
        if (path.startsWith("file://")) path = path.substring(7);
        return path.startsWith("/");
    }

    @WorkerThread
    public static String getMediaStorage(Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme())) return null;
        final String path = uri.getPath();
        if (TextUtils.isEmpty(path)) return null;
        final List<String> storages = AndroidDevices.getExternalStorageDirectories();
        for (String storage : storages) if (path.startsWith(storage)) return storage;
        return null;
    }

    @WorkerThread
    public static DocumentFile findFile(Uri uri) {
        final String storage = getMediaStorage(uri);
        final String treePref = Settings.INSTANCE.getInstance(VLCApplication.getAppContext()).getString("tree_uri_"+ storage, null);
        if (treePref == null) return null;
        final Uri treeUri = Uri.parse(treePref);
        DocumentFile documentFile = DocumentFile.fromTreeUri(VLCApplication.getAppContext(), treeUri);
        String[] parts = (uri.getPath()).split("/");
        for (int i = 3; i < parts.length; i++) {
            if (documentFile != null) documentFile = documentFile.findFile(parts[i]);
            else return null;
        }
        if (documentFile != null) Log.d(TAG, "findFile: write "+documentFile.canWrite());
        return documentFile;

    }

    @WorkerThread
    public static String computeHash(File file) {
        long size = file.length();
        long chunkSizeForFile = Math.min(HASH_CHUNK_SIZE, size);
        long head, tail;
        FileInputStream fis = null;
        FileChannel fileChannel = null;
        try {
            fis = new FileInputStream(file);
            fileChannel = fis.getChannel();
            head = computeHashForChunk(fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, chunkSizeForFile));

            //Alternate way to calculate tail hash for files over 4GB.
            final ByteBuffer bb = ByteBuffer.allocateDirect((int)chunkSizeForFile);
            int read;
            long position = Math.max(size - HASH_CHUNK_SIZE, 0);
            while ((read = fileChannel.read(bb, position)) > 0) position += read;
            bb.flip();
            tail = computeHashForChunk(bb);
            return String.format("%016x", size + head + tail);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            Util.close(fileChannel);
            Util.close(fis);
        }
    }

    @WorkerThread
    private static long computeHashForChunk(ByteBuffer buffer) {
        final LongBuffer longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
        long hash = 0;
        while (longBuffer.hasRemaining()) hash += longBuffer.get();
        return hash;
    }


    @WorkerThread
    public static Uri getUri(Uri data) {
        Uri uri = data;
        final Context ctx = VLCApplication.getAppContext();
        if (data != null && ctx != null && TextUtils.equals(data.getScheme(), "content")) {
            // Mail-based apps - download the stream to a temporary file and play it
            if ("com.fsck.k9.attachmentprovider".equals(data.getHost()) || "gmail-ls".equals(data.getHost())) {
                InputStream is = null;
                OutputStream os = null;
                Cursor cursor = null;
                try {
                    cursor = ctx.getContentResolver().query(data,
                            new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        final String filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)).replace("/", "");
                        if (BuildConfig.DEBUG) Log.i(TAG, "Getting file " + filename + " from content:// URI");
                        is = ctx.getContentResolver().openInputStream(data);
                        if (is == null) return data;
                        os = new FileOutputStream(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename);
                        final byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) >= 0) os.write(buffer, 0, bytesRead);
                        uri = AndroidUtil.PathToUri(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Couldn't download file from mail URI");
                    return null;
                } finally {
                    Util.close(is);
                    Util.close(os);
                    Util.close(cursor);
                }
            }
            // Media or MMS URI
            else if (TextUtils.equals(data.getAuthority(), "media")){
                uri = MediaUtils.INSTANCE.getContentMediaUri(data);
            } else {
                ParcelFileDescriptor inputPFD;
                try {
                    inputPFD = ctx.getContentResolver().openFileDescriptor(data, "r");
                    if (inputPFD == null) return data;
                    uri = AndroidUtil.LocationToUri("fd://" + inputPFD.getFd());
//                    Cursor returnCursor =
//                            getContentResolver().query(data, null, null, null, null);
//                    if (returnCursor != null) {
//                        if (returnCursor.getCount() > 0) {
//                            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
//                            if (nameIndex > -1) {
//                                returnCursor.moveToFirst();
//                                title = returnCursor.getString(nameIndex);
//                            }
//                        }
//                        returnCursor.close();
//                    }
                } catch (FileNotFoundException|IllegalArgumentException e) {
                    Log.e(TAG, "Couldn't understand the intent");
                    return null;
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission is no longer valid");
                    return null;
                }
            }
        }
        return uri;
    }

    @SuppressLint("PrivateApi")
    public static String getStorageTag(final String uuid) {
        if (!AndroidUtil.isMarshMallowOrLater) return null;
        String volumeDescription = null;
        try {
            final StorageManager storageManager = VLCApplication.getAppContext().getSystemService(StorageManager.class);
            final Class<?> classType = storageManager.getClass();
            final Method findVolumeByUuid = classType.getDeclaredMethod("findVolumeByUuid", uuid.getClass());
            findVolumeByUuid.setAccessible(true);
            final Object volumeInfo = findVolumeByUuid.invoke(storageManager, uuid);
            final Class volumeInfoClass = Class.forName("android.os.storage.VolumeInfo");
            final Method getBestVolumeDescription = classType.getDeclaredMethod("getBestVolumeDescription", volumeInfoClass);
            getBestVolumeDescription.setAccessible(true);
            volumeDescription = (String) getBestVolumeDescription.invoke(storageManager, volumeInfo);
        } catch (Throwable ignored) {}
        return volumeDescription;
    }

    public static ArrayList<String> unpackZip(String path, String unzipDirectory)
    {
        InputStream is;
        ZipInputStream zis;
        ArrayList<String> unzippedFiles = new ArrayList();
        try
        {
            is = new FileInputStream(path);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;

            while((ze = zis.getNextEntry()) != null)
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;

                String filename = ze.getName();
                File fileToUnzip = new File(unzipDirectory, filename);
                FileOutputStream fout = new FileOutputStream(fileToUnzip);

                // reading and writing
                while((count = zis.read(buffer)) != -1)
                {
                    baos.write(buffer, 0, count);
                    byte[] bytes = baos.toByteArray();
                    fout.write(bytes);
                    baos.reset();
                }

                unzippedFiles.add(fileToUnzip.getAbsolutePath());
                fout.close();
                zis.closeEntry();
            }

            zis.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }

        return unzippedFiles;
    }
}
