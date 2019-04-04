/*****************************************************************************
 * VideosModel.kt
 *****************************************************************************
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
 *****************************************************************************/

package org.videolan.vlc.viewmodels

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaGroup
import org.videolan.vlc.media.getAll
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.launchChannelUpdate

@ExperimentalCoroutinesApi
open class VideosModel(context: Context, private val group: String?, val folder : Folder?, private val minGroupLen: Int, customSort : Int, customDesc: Boolean?) : MedialibraryModel<MediaWrapper>(context), Medialibrary.MediaCb {

    override val sortKey = "${super.sortKey}_$group"
    override fun canSortByFileNameName() = true
    override fun canSortByDuration() = true
    override fun canSortByLastModified() = folder == null

    private val thumbObs = Observer<MediaWrapper> { media -> if (!updateActor.isClosedForSend) updateActor.offer(MediaUpdate(listOf(media!!))) }

    init {
        sort = if (customSort != Medialibrary.SORT_DEFAULT) customSort
        else Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = customDesc ?: Settings.getInstance(context).getBoolean(sortKey+"_desc", false)
        Medialibrary.lastThumb.observeForever(thumbObs)
        if (medialibrary.isStarted) {
            medialibrary.addMediaCb(this)
            refresh()
        }
    }

    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onMedialibraryIdle() {
        super.onMedialibraryIdle()
        if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater) context.launchChannelUpdate()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    override suspend fun updateList() {
        if (!isActive) return
        dataset.value = withContext(Dispatchers.IO) {
            if (!isActive) return@withContext mutableListOf<MediaWrapper>()
            val list = medialibrary.getVideos(sort, desc)
            val displayList = mutableListOf<MediaWrapper>()
            when {
                folder != null -> displayList.addAll(folder.getAll(Folder.TYPE_FOLDER_VIDEO, sort, desc))
                group !== null -> {
                    val loGroup = group.toLowerCase()
                    for (item in list) {
                        val title = item.title.toLowerCase().let { if (it.startsWith("the")) it.substring(4) else it }
                        if (title.startsWith(loGroup)) displayList.add(item)
                    }
                }
                minGroupLen > 0 -> MediaGroup.group(list, minGroupLen, sort == Medialibrary.SORT_FILENAME).mapTo(displayList) {
                    if (it.size() > 1) { it.description = context.resources.getQuantityString(R.plurals.videos_quantity, it.size(), it.size()) }
                    it.media
                }
                else -> displayList.addAll(list)
            }
            displayList
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addMediaCb(this)
    }

    override fun onCleared() {
        medialibrary.removeMediaCb(this)
        Medialibrary.lastThumb.removeObserver(thumbObs)
        super.onCleared()
    }

    class Factory(
            private val context: Context,
            val group: String?,
            private val folder : Folder?,
            private val minGroupLen : Int,
            private val sort : Int,
            private val desc : Boolean?
    ): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val length = if (minGroupLen == 0) Integer.valueOf(Settings.getInstance(context).getString("video_min_group_length", "6")!!) else minGroupLen
            @Suppress("UNCHECKED_CAST")
            return VideosModel(context.applicationContext, group, folder, length, sort, desc) as T
        }
    }

    companion object {
        fun get(
                context: Context,
                fragment: Fragment,
                sort : Int = Medialibrary.SORT_DEFAULT,
                minGroupLen : Int = 0,
                desc : Boolean? = null,
                group: String? = null,
                folder: Folder? = null
        ) : VideosModel {
            return ViewModelProviders.of(fragment, Factory(context, group, folder, minGroupLen, sort, desc)).get(VideosModel::class.java)
        }
    }
}
