/*******************************************************************************
 *  BrowserFavRepository.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 ******************************************************************************/

package org.videolan.vlc.repository

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.content.Context
import android.net.Uri
import android.support.annotation.WorkerThread
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.database.BrowserFavDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.util.Constants.TYPE_LOCAL_FAV
import org.videolan.vlc.util.Constants.TYPE_NETWORK_FAV
import org.videolan.vlc.util.VLCIO
import org.videolan.vlc.util.uiJob
import java.util.*


class BrowserFavRepository @JvmOverloads constructor(context: Context,
                                                     val mediaDatabase: MediaDatabase = MediaDatabase.getDatabase(context),
                                                     private val browserFavDao: BrowserFavDao = mediaDatabase.browserFavDao()
) {


    private val networkFavs by lazy { browserFavDao.getAllNetwrokFavs() }

    fun addNetworkFavItem(uri: Uri, title: String, iconUrl: String?): Job {
        return launch(VLCIO) {
            (browserFavDao.insert(BrowserFav(uri, TYPE_NETWORK_FAV, title, iconUrl)))
        }
    }

    fun addLocalFavItem(uri: Uri, title: String, iconUrl: String?): Job {
        return launch(VLCIO) {
            browserFavDao.insert(BrowserFav(uri, TYPE_LOCAL_FAV, title, iconUrl))
        }
    }

    val networkFavorites by lazy {
        MediatorLiveData<List<MediaWrapper>>().apply {
            addSource(networkFavs) { value = createMediaWrapperObjects(it).filterNetworkFavs() }
            addSource(ExternalMonitor.connected) {
                uiJob {
                    val favList = getCurrentFavorites(networkFavs)
                    if (favList.isNotEmpty()) value = if (it == true) favList.filterNetworkFavs() else emptyList()
                }
            }
        }
    }

    val browserFavorites by lazy {
        browserFavDao.getAll()
    }

    val localFavorites by lazy {
        browserFavDao.getAllLocalFavs()
    }

    @WorkerThread
    suspend fun getCurrentFavorites(favsType: LiveData<List<BrowserFav>>) = withContext(VLCIO) {
        favsType.value?.let {
            createMediaWrapperObjects(it)
        } ?: emptyList()}

    @WorkerThread
    fun browserFavExists(uri: Uri): Boolean = browserFavDao.get(uri).isNotEmpty()

    private fun createMediaWrapperObjects(allBrowserFavs: List<BrowserFav>?): List<MediaWrapper> {
        return allBrowserFavs?.map { (uri, _, title, iconUrl) ->
            MediaWrapper(uri).apply {
                setDisplayTitle(Uri.decode(title))
                type = MediaWrapper.TYPE_DIR
                iconUrl?.let { artworkURL = Uri.decode(it) }
            }
        } ?: emptyList()
    }

    fun deleteBrowserFav(uri: Uri) {
        browserFavDao.delete(uri)
    }

    private fun List<MediaWrapper>.filterNetworkFavs() : List<MediaWrapper> {
        return when {
            isEmpty() -> this
            !ExternalMonitor.isConnected() -> emptyList()
            !ExternalMonitor.allowLan() -> {
                val schemes = Arrays.asList("ftp", "sftp", "ftps", "http", "https")
                mutableListOf<MediaWrapper>().apply { this@filterNetworkFavs.filterTo(this) { schemes.contains(it.uri.scheme) } }
            }
            else -> this
        }
    }
}
