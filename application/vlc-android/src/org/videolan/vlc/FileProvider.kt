package org.videolan.vlc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import java.io.File
import java.io.FileNotFoundException

private const val TAG = "VLC/FileProvider"
private const val THUMB_PROVIDER_AUTHORITY = "${BuildConfig.APP_ID}.thumbprovider"

class FileProvider : ContentProvider() {
    override fun insert(uri: Uri, values: ContentValues?) = Uri.EMPTY!!

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        throw UnsupportedOperationException("Not supported by this provider")
    }

    override fun onCreate() = true

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun getType(uri: Uri) = "image/${uri.path?.substringAfterLast('.')}"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = uri.path ?: throw SecurityException("Illegal access")
        if (path.contains("..")) throw SecurityException("Illegal access")
        if (!path.startsWith(AppContextProvider.appContext.getExternalFilesDir(null)!!.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME)) throw SecurityException("Illegal access")
        val file = File(path)
        if (!AndroidDevices.mountBL.any { file.canonicalPath.startsWith(it) }) throw SecurityException("Illegal access")
        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        throw FileNotFoundException(path)
    }
}

fun getFileUri(path: String) = Uri.Builder()
        .scheme("content")
        .authority(THUMB_PROVIDER_AUTHORITY)
        .path(path)
        .build()!!

fun isPathValid(path: String): Boolean {
    val file = File(path)
    return AndroidDevices.mountBL.any { file.canonicalPath.startsWith(it) } && file.canRead()
}