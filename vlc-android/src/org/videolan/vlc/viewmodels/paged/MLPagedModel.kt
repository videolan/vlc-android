/*****************************************************************************
 * MLPagedModel.kt
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.annotation.MainThread
import androidx.collection.SparseArrayCompat
import androidx.lifecycle.LiveData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.SortableModel

typealias HeadersIndex = SparseArrayCompat<String>

@Suppress("LeakingThis")
@ExperimentalCoroutinesApi
abstract class MLPagedModel<T : MediaLibraryItem>(context: Context) : SortableModel(context), Medialibrary.OnMedialibraryReadyListener, Medialibrary.OnDeviceChangeListener {
    protected val medialibrary = Medialibrary.getInstance()
    abstract val provider : MedialibraryProvider<T>

    private lateinit var restoreJob: Job
    val liveHeaders
    get() = provider.liveHeaders

    val pagedList
    get() = provider.pagedList
    val loading : LiveData<Boolean>
    get() = provider.loading

    init {
        medialibrary.addOnMedialibraryReadyListener(this)
        medialibrary.addOnDeviceChangeListener(this)
    }

    override fun canSortByName() = provider.canSortByName()
    override fun canSortByFileNameName() = provider.canSortByFileNameName()
    override fun canSortByDuration() = provider.canSortByDuration()
    override fun canSortByInsertionDate() = provider.canSortByInsertionDate()
    override fun canSortByLastModified() = provider.canSortByLastModified()
    override fun canSortByReleaseDate() = provider.canSortByReleaseDate()
    override fun canSortByFileSize() = provider.canSortByFileSize()
    override fun canSortByArtist() = provider.canSortByArtist()
    override fun canSortByAlbum () = provider.canSortByAlbum ()
    override fun canSortByPlayCount() = provider.canSortByPlayCount()

    override fun onMedialibraryReady() { refresh() }

    override fun onMedialibraryIdle() { refresh() }

    override fun onDeviceChange() { refresh() }

    override fun onCleared() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        super.onCleared()
    }

    override fun sort(sort: Int) {
        if (this.sort != sort) {
            this.sort = sort
            desc = false
        } else desc = !desc
        refresh()
        Settings.getInstance(context).edit()
                .putInt(sortKey, sort)
                .putBoolean("${sortKey}_desc", desc)
                .apply()
    }

    fun isFiltering() = filterQuery != null

    override fun filter(query: String?) {
        filterQuery = query
        refresh()
    }
    override fun restore() {
        restoreJob = launch {
            delay(500L)
            if (filterQuery != null) {
                filterQuery = null
                refresh()
            }
        }
    }

    fun isEmpty() = pagedList.value.isNullOrEmpty()

    override fun refresh(): Boolean {
        if (this::restoreJob.isInitialized && restoreJob.isActive) restoreJob.cancel()
        launch { provider.refresh() }
        return true
    }

    protected fun completeHeaders(list: Array<T>, startposition: Int) = provider.completeHeaders(list, startposition)

    @MainThread
    fun getSectionforPosition(position: Int) = provider.getSectionforPosition(position)

    @MainThread
    fun isFirstInSection(position: Int) = provider.isFirstInSection(position)

    @MainThread
    fun getPositionForSection(position: Int) = provider.getPositionForSection(position)

    @MainThread
    fun getPositionForSectionByName(header: String) = provider.getPositionForSectionByName(header)

    @MainThread
    fun getHeaderForPostion(position: Int) = provider.getHeaderForPostion(position)
}
