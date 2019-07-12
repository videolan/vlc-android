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
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.media.MediaUtils
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import java.util.zip.ZipInputStream

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object FileUtils {

    val TAG = "VLC/FileUtils"

    /**
     * Size of the chunks that will be hashed in bytes (64 KB)
     */
    private const val HASH_CHUNK_SIZE = 64 * 1024

    interface Callback {
        fun onResult(success: Boolean)
    }

    fun getFileNameFromPath(path: String?): String {
        var path: String? = path ?: return ""
        var index = path!!.lastIndexOf('/')
        if (index == path.length - 1) {
            path = path.substring(0, index)
            index = path.lastIndexOf('/')
        }
        return if (index > -1)
            path.substring(index + 1)
        else
            path
    }

    fun getParent(path: String?): String? {
        if (path == null || TextUtils.equals("/", path))
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
        return if (!TextUtils.equals(uri.scheme, "file") || !uri.path!!.startsWith("/sdcard")) uri else Uri.parse(uri.toString().replace("/sdcard", AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
    }

    @WorkerThread
    fun getPathFromURI(contentUri: Uri): String {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = VLCApplication.appContext.contentResolver.query(contentUri, proj, null, null, null)
            if (cursor == null || cursor.count == 0)
                return ""
            val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            return cursor.getString(column_index)
        } catch (e: IllegalArgumentException) {
            return ""
        } catch (e: SecurityException) {
            return ""
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
    }

    fun copyLua(context: Context, force: Boolean) {
        runIO(Runnable {
            val destinationFolder = context.getDir("vlc",
                    Context.MODE_PRIVATE).absolutePath + "/.share/lua"
            val am = context.assets
            copyAssetFolder(am, "lua", destinationFolder, force)
        })
    }

    @WorkerThread
    internal fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String, force: Boolean): Boolean {
        try {
            val files = assetManager.list(fromAssetPath)
            if (files!!.isEmpty()) return false
            File(toPath).mkdirs()
            var res = true
            for (file in files)
                res = if (file.contains("."))
                    res and copyAsset(assetManager,
                            "$fromAssetPath/$file",
                            "$toPath/$file",
                            force)
                else
                    res and copyAssetFolder(assetManager,
                            "$fromAssetPath/$file",
                            "$toPath/$file",
                            force)
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
            copyFile(`in`!!, out)
            out.flush()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            Util.close(`in`)
            Util.close(out)
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
            for (file in filesList)
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
                Util.close(inputStream)
                Util.close(out)
            }
            return false
        }
        return ret
    }

    @WorkerThread
    fun deleteFile(uri: Uri): Boolean {
        if (!AndroidUtil.isLolliPopOrLater || uri.path!!.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) return deleteFile(uri.path)
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
            for (child in file.listFiles()) deleted = deleted and deleteFile(child)
            if (deleted) deleted = deleted and file.delete()
        } else {
            val cr = VLCApplication.appContext.contentResolver
            try {
                deleted = cr.delete(MediaStore.Files.getContentUri("external"),
                        MediaStore.Files.FileColumns.DATA + "=?", arrayOf(file.path)) > 0
            } catch (ignored: IllegalArgumentException) {
                deleted = false
            } catch (ignored: SecurityException) {
                deleted = false
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
            var success = true
            if (fileOrDirectory.isDirectory) {
                for (child in fileOrDirectory.listFiles())
                    asyncRecursiveDelete(child, null)
                success = fileOrDirectory.delete()
            } else {
                success = deleteFile(fileOrDirectory.path)
            }
            callback?.onResult(success)
        })
    }

    fun canSave(mw: AbstractMediaWrapper?): Boolean {
        if (mw == null || mw.uri == null) return false
        val scheme = mw.uri.scheme
        return (TextUtils.equals(scheme, "file") || TextUtils.equals(scheme, "smb")
                || TextUtils.equals(scheme, "nfs") || TextUtils.equals(scheme, "ftp")
                || TextUtils.equals(scheme, "ftps") || TextUtils.equals(scheme, "sftp"))
    }

    @WorkerThread
    fun canWrite(uri: Uri?): Boolean {
        if (uri == null) return false
        return if (TextUtils.equals("file", uri.scheme)) canWrite(uri.path) else TextUtils.equals("content", uri.scheme) && canWrite(getPathFromURI(uri))
    }

    @WorkerThread
    fun canWrite(path: String?): Boolean {
        var path = path
        if (TextUtils.isEmpty(path)) return false
        if (path!!.startsWith("file://")) path = path.substring(7)
        return path.startsWith("/")
    }

    @WorkerThread
    fun getMediaStorage(uri: Uri?): String? {
        if (uri == null || "file" != uri.scheme) return null
        val path = uri.path
        if (TextUtils.isEmpty(path)) return null
        val storages = AndroidDevices.externalStorageDirectories
        for (storage in storages) if (path!!.startsWith(storage)) return storage
        return null
    }

    @WorkerThread
    fun findFile(uri: Uri): DocumentFile? {
        val storage = getMediaStorage(uri)
        val treePref = Settings.getInstance(VLCApplication.appContext).getString("tree_uri_" + storage!!, null)
                ?: return null
        val treeUri = Uri.parse(treePref)
        var documentFile = DocumentFile.fromTreeUri(VLCApplication.appContext, treeUri)
        val parts = uri.path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in 3 until parts.size) {
            if (documentFile != null)
                documentFile = documentFile.findFile(parts[i])
            else
                return null
        }
        return documentFile

    }

    @WorkerThread
    fun computeHash(file: File): String? {
        val size = file.length()
        val chunkSizeForFile = Math.min(HASH_CHUNK_SIZE.toLong(), size)
        val head: Long
        val tail: Long
        var fis: FileInputStream? = null
        var fileChannel: FileChannel? = null
        try {
            fis = FileInputStream(file)
            fileChannel = fis.channel
            head = computeHashForChunk(fileChannel!!.map(FileChannel.MapMode.READ_ONLY, 0, chunkSizeForFile))

            //Alternate way to calculate tail hash for files over 4GB.
            val bb = ByteBuffer.allocateDirect(chunkSizeForFile.toInt())
            var position = Math.max(size - HASH_CHUNK_SIZE, 0)
            var read = fileChannel.read(bb, position)
            while (read > 0) {
                position += read.toLong()
                read = fileChannel.read(bb, position)
            }
            bb.flip()
            tail = computeHashForChunk(bb)
            return String.format("%016x", size + head + tail)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            Util.close(fileChannel)
            Util.close(fis)
        }
    }

    @WorkerThread
    private fun computeHashForChunk(buffer: ByteBuffer): Long {
        val longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        var hash: Long = 0
        while (longBuffer.hasRemaining()) hash += longBuffer.get()
        return hash
    }


    @WorkerThread
    fun getUri(data: Uri?): Uri? {
        var uri = data
        val ctx = VLCApplication.appContext
        if (data != null && ctx != null && TextUtils.equals(data.scheme, "content")) {
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
                        if (inputStream == null) return data
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
                    Log.e(TAG, "Couldn't download file from mail URI")
                    return null
                } finally {
                    Util.close(inputStream)
                    Util.close(os)
                    Util.close(cursor)
                }
            } else if (TextUtils.equals(data.authority, "media")) {
                uri = MediaUtils.getContentMediaUri(data)
            } else {
                val inputPFD: ParcelFileDescriptor?
                try {
                    inputPFD = ctx.contentResolver.openFileDescriptor(data, "r")
                    if (inputPFD == null) return data
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
                    Log.e(TAG, "Couldn't understand the intent")
                    return null
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Couldn't understand the intent")
                    return null
                } catch (e: NullPointerException) {
                    Log.e(TAG, "Couldn't understand the intent")
                    return null
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission is no longer valid")
                    return null
                }

            }// Media or MMS URI
        }
        return uri
    }

    @SuppressLint("PrivateApi")
    fun getStorageTag(uuid: String): String? {
        if (!AndroidUtil.isMarshMallowOrLater) return null
        var volumeDescription: String? = null
        try {
            val storageManager = VLCApplication.appContext.getSystemService(StorageManager::class.java)
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
}

fun String?.getParentFolder(): String? {
    if (this == null || TextUtils.equals("/", this)) return this
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

