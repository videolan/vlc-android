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

package org.videolan.vlc.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.isExternalStorageManager
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import java.io.*
import java.lang.Runnable
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    const val TAG = "VLC/FileUtils"

    interface Callback {
        fun onResult(success: Boolean)
    }

    fun getFileNameFromPath(filePath: String?) =  filePath?.substringAfterLast('/') ?: ""

    fun getParent(path: String?): String? {
        if (path == null || path == "/")
            return path
        var parentPath: String = path
        if (parentPath.endsWith("/"))
            parentPath = parentPath.substring(0, parentPath.length - 1)
        val index = parentPath.lastIndexOf('/')
        if (index > 0) {
            parentPath = parentPath.substring(0, index)
        } else if (index == 0)
            parentPath = "/"
        return parentPath
    }

    /*
     * Convert file:// uri from real path to emulated FS path.
     */
    fun convertLocalUri(uri: Uri): Uri {
        return if (uri.scheme != "file" || !uri.path!!.startsWith("/sdcard")) uri else uri.toString().replace("/sdcard", AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY).toUri()
    }

    @WorkerThread
    fun getPathFromURI(contentUri: Uri): String {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = AppContextProvider.appContext.contentResolver.query(contentUri, proj, null, null, null)
            if (cursor == null || cursor.count == 0)
                return ""
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            return Uri.fromFile(File(cursor.getString(columnIndex))).toString()
        } catch (e: IllegalArgumentException) {
            return ""
        } catch (e: SecurityException) {
            return ""
        } catch (e: SQLiteException) {
            return ""
        } catch (e: NullPointerException) {
            return ""
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
    }

    fun copyHrtfs(context: Context, force: Boolean) {
        AppScope.launch(Dispatchers.IO) {
            val destinationFolder = context.getDir("vlc",
                    Context.MODE_PRIVATE).absolutePath + "/.share/hrtfs"
            val am = context.assets
            copyAssetFolder(am, "hrtfs", destinationFolder, force)
        }
    }

    fun copyLua(context: Context, force: Boolean) {
        AppScope.launch(Dispatchers.IO) {
            val destinationFolder = context.getDir("vlc",
                    Context.MODE_PRIVATE).absolutePath + "/.share/lua"
            val am = context.assets
            copyAssetFolder(am, "lua", destinationFolder, force)
        }
    }

    @WorkerThread
    fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String, force: Boolean): Boolean {
        try {
            val files = assetManager.list(fromAssetPath)
            if (files.isNullOrEmpty()) return false
            File(toPath).mkdirs()
            var res = true
            for (file in files) {
                res = if (file.contains(".")) {
                    res and copyAsset(
                        assetManager,
                        "$fromAssetPath/$file",
                        "$toPath/$file",
                        force
                    )
                } else {
                    res and copyAssetFolder(
                        assetManager,
                        "$fromAssetPath/$file",
                        "$toPath/$file",
                        force
                    )
                }
            }
            return res
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    @WorkerThread
    private fun copyAsset(assetManager: AssetManager, fromAssetPath: String, toPath: String, force: Boolean): Boolean {
        val destFile = File(toPath)
        if (!force && destFile.exists()) return true
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = assetManager.open(fromAssetPath)
            destFile.createNewFile()
            out = FileOutputStream(toPath)
            copyFile(`in`, out)
            out.flush()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            CloseableUtils.close(`in`)
            CloseableUtils.close(out)
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun copyFile(inputStream: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read = inputStream.read(buffer)
        while (read != -1) {
            out.write(buffer, 0, read)
            read = inputStream.read(buffer)
        }
    }

    @WorkerThread
    fun copyFile(src: File, dst: File): Boolean {
        var ret = true
        if (src.isDirectory) {
            val filesList = src.listFiles()
            dst.mkdirs()
            for (file in filesList ?: arrayOf())
                ret = ret and copyFile(file, File(dst, file.name))
        } else if (src.isFile) {
            var inputStream: InputStream? = null
            var out: OutputStream? = null
            try {
                inputStream = BufferedInputStream(FileInputStream(src))
                out = BufferedOutputStream(FileOutputStream(dst))

                // Transfer bytes from in to out
                val buf = ByteArray(1024)
                var len = inputStream.read(buf)
                while (len > 0) {
                    out.write(buf, 0, len)
                    len = inputStream.read(buf)
                }
                return true
            } catch (ignored: IOException) {
            } finally {
                CloseableUtils.close(inputStream)
                CloseableUtils.close(out)
            }
            return false
        }
        return ret
    }

    @WorkerThread
    fun deleteFile(uri: Uri): Boolean {
        if (isExternalStorageManager() || !AndroidUtil.isLolliPopOrLater || uri.path!!.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) return deleteFile(uri.path)
        val docFile = findFile(uri)
        if (docFile != null)
            try {
                return docFile.delete()
            } catch (ignored: Exception) {
            }
        return false
    }

    @WorkerThread
    fun deleteFile(path: String?) = path?.let { deleteFile(File(it)) } ?: false

    @WorkerThread
    fun deleteFile(file: File): Boolean {
        var deleted: Boolean
        //Delete from Android Medialib, for consistency with device MTP storing and other apps listing content:// media
        if (file.isDirectory) {
            deleted = true
            for (child in file.listFiles() ?: arrayOf()) deleted = deleted and deleteFile(child)
            if (deleted) deleted = deleted and file.delete()
        } else {
            val cr = AppContextProvider.appContext.contentResolver
            deleted = try {
                cr.delete(MediaStore.Files.getContentUri("external"),
                        MediaStore.Files.FileColumns.DATA + "=?", arrayOf(file.path)) > 0
            } catch (ignored: IllegalArgumentException) {
                false
            } catch (ignored: SecurityException) {
                false
            }
            // Can happen on some devices...
            if (file.exists()) deleted = deleted or file.delete()
        }
        return deleted
    }

    fun String.isInternalStorage() = startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)

    private fun asyncRecursiveDelete(path: String, callback: Callback?) {
        asyncRecursiveDelete(File(path), callback)
    }

    fun asyncRecursiveDelete(path: String) {
        asyncRecursiveDelete(path, null)
    }

    private fun asyncRecursiveDelete(fileOrDirectory: File, callback: Callback?) {
        runIO(Runnable {
            if (!fileOrDirectory.exists() || !fileOrDirectory.canWrite())
                return@Runnable
            val success: Boolean
            if (fileOrDirectory.isDirectory) {
                for (child in fileOrDirectory.listFiles() ?: arrayOf())
                    asyncRecursiveDelete(child, null)
                success = fileOrDirectory.delete()
            } else {
                success = deleteFile(fileOrDirectory.path)
            }
            callback?.onResult(success)
        })
    }

    fun canSave(mw: MediaWrapper?): Boolean {
        if (mw == null || mw.uri == null) return false
        val scheme = mw.uri.scheme
        return scheme in arrayOf("file", "smb", "nfs", "ftp", "ftps", "ftpes", "sftp", "upnp")
    }

    @WorkerThread
    fun canWrite(uri: Uri?): Boolean {
        if (uri == null) return false
        return if (uri.scheme == "file") canWrite(uri.path) else uri.scheme == "content" && canWrite(getPathFromURI(uri))
    }

    @WorkerThread
    fun canWrite(writePath: String?): Boolean {
        val path = writePath ?: return false
        if (path.isEmpty()) return false
        return path.removeFileScheme().startsWith("/")
    }

    @WorkerThread
    fun getMediaStorage(uri: Uri?): String? {
        if (uri == null || "file" != uri.scheme) return null
        val path = uri.path
        if (path.isNullOrEmpty()) return null
        val storages = AndroidDevices.externalStorageDirectories
        for (storage in storages) if (path.startsWith(storage)) return storage
        return null
    }

    @WorkerThread
    fun findFile(uri: Uri): DocumentFile? {
        uri.path?.let { path ->
            val context = (AppContextProvider.appContext as Context?) ?: return null
            val treePref = getMediaStorage(uri)?.let { Settings.getInstance(context).getString("tree_uri_$it", null) } ?: return null
            val treeUri = treePref.toUri()
            var documentFile = DocumentFile.fromTreeUri(context, treeUri)
            val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in 3 until parts.size) {
                if (documentFile != null)
                    documentFile = documentFile.findFile(parts[i])
                else
                    return null
            }
            return documentFile
        }
        return null
    }


    @WorkerThread
    fun getUri(data: Uri?): Uri? {
        var uri = data
        val ctx = AppContextProvider.appContext
        if (data != null && data.scheme == "content") {
            // Mail-based apps - download the stream to a temporary file and play it
            if ("com.fsck.k9.attachmentprovider" == data.host || "gmail-ls" == data.host) {
                var inputStream: InputStream? = null
                var os: OutputStream? = null
                var cursor: Cursor? = null
                try {
                    cursor = ctx.contentResolver.query(data,
                            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                    if (cursor != null && cursor.moveToFirst()) {
                        val filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)).replace("/", "")
                        if (BuildConfig.DEBUG) Log.i(TAG, "Getting file $filename from content:// URI")
                        inputStream = ctx.contentResolver.openInputStream(data)
                        if (inputStream == null) {
                            Log.i("FileUtils", "Expanding uri: $data to $data")
                            return data
                        }
                        os = FileOutputStream(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename)
                        val buffer = ByteArray(1024)
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead >= 0) {
                            os.write(buffer, 0, bytesRead)
                            bytesRead = inputStream.read(buffer)
                        }
                        uri = AndroidUtil.PathToUri(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't download file from mail URI: $data")
                    return null
                } finally {
                    CloseableUtils.close(inputStream)
                    CloseableUtils.close(os)
                    CloseableUtils.close(cursor)
                }
            } else if (data.host == "com.amaze.filemanager" && data.path != null) {
                uri = Uri.parse(data.path!!.replace("/storage_root", "file://"))
            } else if (data.authority == "media") {
                uri = MediaUtils.getContentMediaUri(data)
            } else if (data.authority == ctx.getString(R.string.tv_provider_authority)) {
                val medialibrary = Medialibrary.getInstance()
                val media = medialibrary.getMedia(data.lastPathSegment!!.toLong())
                uri = media.uri
            } else {
                uri = MediaUtils.getContentMediaUri(data)
                if (uri != null && uri != data)
                    return uri
                val inputPFD: ParcelFileDescriptor?
                try {
                    inputPFD = ctx.contentResolver.openFileDescriptor(data, "r")
                    if (inputPFD == null) {
                        Log.i("FileUtils", "Expanding uri: $data to $data")
                        return data
                    }
                    uri = AndroidUtil.LocationToUri("fd://" + inputPFD.fd)
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
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "${e.message} for $data", e)
                    return null
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "${e.message} for $data", e)
                    return null
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "${e.message} for $data", e)
                    return null
                } catch (e: NullPointerException) {
                    Log.e(TAG, "${e.message} for $data", e)
                    return null
                } catch (e: SecurityException) {
                    Log.e(TAG, "${e.message} for $data", e)
                    return null
                }
            }// Media or MMS URI
        }
        Log.i("FileUtils", "Expanding uri: $data to $uri")
        return uri
    }

    @SuppressLint("PrivateApi")
    fun getStorageTag(uuid: String): String? {
        if (!AndroidUtil.isMarshMallowOrLater) return null
        var volumeDescription: String? = null
        try {
            val storageManager = AppContextProvider.appContext.getSystemService(StorageManager::class.java)
            val classType = storageManager.javaClass
            val findVolumeByUuid = classType.getDeclaredMethod("findVolumeByUuid", uuid.javaClass)
            findVolumeByUuid.isAccessible = true
            val volumeInfo = findVolumeByUuid.invoke(storageManager, uuid)
            val volumeInfoClass = Class.forName("android.os.storage.VolumeInfo")
            val getBestVolumeDescription = classType.getDeclaredMethod("getBestVolumeDescription", volumeInfoClass)
            getBestVolumeDescription.isAccessible = true
            volumeDescription = getBestVolumeDescription.invoke(storageManager, volumeInfo) as String
        } catch (ignored: Throwable) {
        }

        return volumeDescription
    }

    suspend fun unpackZip(path: String, unzipDirectory: String): ArrayList<String> = withContext(Dispatchers.IO) {
        val fis: InputStream
        val zis: ZipInputStream
        val unzippedFiles = ArrayList<String>()
        File(unzipDirectory).mkdirs()
        try {
            fis = FileInputStream(path)
            zis = ZipInputStream(BufferedInputStream(fis))
            var ze = zis.nextEntry

            while (ze != null) {
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var count = zis.read(buffer)

                val filename = ze.name.replace('/', ' ')
                if (filename.endsWith(".nfo")) {
                    zis.closeEntry()
                    ze = zis.nextEntry
                    continue
                }
                val fileToUnzip = File(unzipDirectory, filename)
                val fout = FileOutputStream(fileToUnzip)

                // reading and writing
                while (count != -1) {
                    baos.write(buffer, 0, count)
                    val bytes = baos.toByteArray()
                    fout.write(bytes)
                    baos.reset()
                    count = zis.read(buffer)
                }

                unzippedFiles.add(fileToUnzip.absolutePath)
                fout.close()
                zis.closeEntry()
                ze = zis.nextEntry
            }
            zis.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        unzippedFiles
    }

    const val BUFFER = 2048
    fun zip(files: Array<String>, zipFileName: String):Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(
                    FileOutputStream(zipFileName))).use { out ->
                val data = ByteArray(BUFFER)
                for (i in files.indices) {
                    val fi = FileInputStream(files[i])
                    BufferedInputStream(fi, BUFFER).use { origin ->
                        val entry = ZipEntry(files[i].substring(files[i].lastIndexOf("/") + 1))
                        out.putNextEntry(entry)
                        var count = origin.read(data, 0, BUFFER)

                        while (count != -1) {
                            out.write(data, 0, count)
                            count = origin.read(data, 0, BUFFER)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun zipWithName(files: Array<Pair<String, String>>, zipFileName: String): Boolean {
        return try {
            File(zipFileName).parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFileName))).use { out ->
                val data = ByteArray(BUFFER)
                for (i in files.indices) {
                    val fi = FileInputStream(files[i].first)
                    BufferedInputStream(fi, BUFFER).use { origin ->
                        val entry = ZipEntry(files[i].second)
                        out.putNextEntry(entry)
                        var count = origin.read(data, 0, BUFFER)

                        while (count != -1) {
                            out.write(data, 0, count)
                            count = origin.read(data, 0, BUFFER)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            false
        }
    }

    @Throws(Exception::class)
    fun convertStreamToString(`is`: InputStream): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()
        return sb.toString()
    }

    @Throws(Exception::class)
    fun getStringFromFile(filePath: String): String {
        val fl = File(filePath)
        val fin = FileInputStream(fl)
        val ret = convertStreamToString(fin)
        //Make sure you close all streams.
        fin.close()
        return ret
    }

    fun getSoundFontExtensions() = arrayOf("sf2", "sf3")
}

fun String?.getParentFolder(): String? {
    if (this == null || this == "/") return this
    var parentPath: String = this
    if (parentPath.endsWith("/"))
        parentPath = parentPath.substring(0, parentPath.length - 1)
    val index = parentPath.lastIndexOf('/')
    if (index > 0) {
        parentPath = parentPath.substring(0, index)
    } else if (index == 0)
        parentPath = "/"
    return parentPath
}

fun String.encodeMrlWithTrailingSlash():String {
    val encoded = Tools.encodeVLCMrl(this)
    return if (encoded.endsWith("/")) encoded else encoded.addTrailingSlashIfNeeded()
}

fun Uri?.isSoundFont():Boolean {
    this?.lastPathSegment?.lowercase()?.let { lastPathSegment ->
        FileUtils.getSoundFontExtensions().forEach {
            if (lastPathSegment.endsWith(it)) return true
        }
    }
    return false
}

fun InputStream.toByteArray(): ByteArray {
    val buffer = ByteArrayOutputStream()

    var nRead: Int
    val data = ByteArray(16384)

    while (this.read(data, 0, data.size).also { nRead = it } != -1) {
        buffer.write(data, 0, nRead)
    }

    return buffer.toByteArray()
}