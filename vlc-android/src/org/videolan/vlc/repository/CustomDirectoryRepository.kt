package org.videolan.vlc.repository

import android.content.Context
import android.support.annotation.WorkerThread
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.CustomDirectoryDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.models.CustomDirectory
import org.videolan.vlc.util.VLCIO

class CustomDirectoryRepository (private val customDirectoryDao: CustomDirectoryDao) {

    fun addCustomDirectory(path: String): Job {
        return launch(VLCIO) {
            customDirectoryDao.insert(CustomDirectory(path))
        }
    }

    @WorkerThread
    fun getCustomDirectories(): List<CustomDirectory> {
        return customDirectoryDao.getAll()
    }

    fun deleteCustomDirectory(path: String) {
       launch(VLCIO) { customDirectoryDao.delete(CustomDirectory(path)) }
    }

    @WorkerThread
    fun customDirectoryExists(path: String) = customDirectoryDao.get(path).isNotEmpty()

    companion object : SingletonHolder<CustomDirectoryRepository, Context>({ CustomDirectoryRepository(MediaDatabase.getInstance(it).customDirectoryDao()) })
}