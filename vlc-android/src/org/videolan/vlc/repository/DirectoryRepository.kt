package org.videolan.vlc.repository

import android.content.Context
import android.text.TextUtils
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.tools.IOScopedObject
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.R
import org.videolan.vlc.database.CustomDirectoryDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.models.CustomDirectory
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY
import org.videolan.vlc.util.AndroidDevices.getExternalStorageDirectories
import org.videolan.vlc.util.FileUtils
import java.io.File

class DirectoryRepository (private val customDirectoryDao: CustomDirectoryDao) : IOScopedObject() {

    fun addCustomDirectory(path: String): Job = launch {
            customDirectoryDao.insert(CustomDirectory(path))
    }

    suspend fun getCustomDirectories() = withContext(coroutineContext) { customDirectoryDao.getAll() }

    fun deleteCustomDirectory(path: String) = launch { customDirectoryDao.delete(CustomDirectory(path)) }

    suspend fun customDirectoryExists(path: String) = withContext(coroutineContext) { customDirectoryDao.get(path).isNotEmpty() }

    suspend fun getMediaDirectoriesList(context: Context): List<MediaWrapper> {
        val storages = getMediaDirectories()

        return storages.filter { File(it).exists() }.map {
            val directory = MediaWrapper(AndroidUtil.PathToUri(it))
            directory.type = MediaWrapper.TYPE_DIR
            if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, it)) {
                directory.setDisplayTitle(context.resources.getString(R.string.internal_memory))
            } else {
                val deviceName = FileUtils.getStorageTag(directory.title)
                if (deviceName != null) directory.setDisplayTitle(deviceName)
            }
            directory
        }.toList()
    }

    suspend fun getMediaDirectories(): Array<String> {
        val list = mutableListOf<String>()

        list.add(EXTERNAL_PUBLIC_DIRECTORY)
        list.addAll(getExternalStorageDirectories())
        list.addAll(getCustomDirectories().map { it.path })

        return list.toTypedArray()
    }

    companion object : SingletonHolder<DirectoryRepository, Context>({ DirectoryRepository(MediaDatabase.getInstance(it).customDirectoryDao()) })
}