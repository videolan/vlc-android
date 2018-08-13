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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.support.v4.app.Fragment
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaGroup
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.VLCIO

open class VideosModel(context: Context, private val group: String?, private val minGroupLen: Int, customSort : Int, customDesc: Boolean?) : MedialibraryModel<MediaWrapper>(context), MediaAddedCb {

    override val sortKey = "${super.sortKey}_$group"
    override fun canSortByFileNameName() = true
    override fun canSortByDuration() = true
    override fun canSortByLastModified() = true

    private val thumbObs = Observer<MediaWrapper> { media -> updateActor.offer(MediaUpdate(listOf(media!!))) }

    init {
        sort = if (customSort != Medialibrary.SORT_DEFAULT) customSort
        else Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = customDesc ?: Settings.getInstance(context).getBoolean(sortKey+"_desc", false)
        Medialibrary.lastThumb.observeForever(thumbObs)
    }

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaListAddition(mediaList!!.filter { it.type == MediaWrapper.TYPE_VIDEO }))
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaUpdate(mediaList!!.filter { it.type == MediaWrapper.TYPE_VIDEO }))
    }

    override suspend fun updateList() {
        dataset.value = withContext(VLCIO) {
            val list = medialibrary.getVideos(sort, desc)
            val displayList = mutableListOf<MediaWrapper>()
            when {
                group !== null -> {
                    val loGroup = group.toLowerCase()
                    for (item in list) {
                        val title = item.title.toLowerCase().let { if (it.startsWith("the")) it.substring(4) else it }
                        if (title.startsWith(loGroup)) displayList.add(item)
                    }
                }
                minGroupLen > 0 -> MediaGroup.group(list, minGroupLen).mapTo(displayList) {
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
        medialibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO)
        medialibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_VIDEO)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeMediaAddedCb()
        medialibrary.removeMediaUpdatedCb()
        Medialibrary.lastThumb.removeObserver(thumbObs)
    }

    class Factory(private val context: Context, val group: String?, private val minGroupLen : Int, private val sort : Int, private val desc : Boolean?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            val length = if (minGroupLen == 0) Integer.valueOf(Settings.getInstance(context).getString("video_min_group_length", "6")) else minGroupLen
            return VideosModel(context.applicationContext, group, length, sort, desc) as T
        }
    }

    companion object {
        fun get(
                context: Context,
                fragment: Fragment,
                group: String?,
                sort : Int = Medialibrary.SORT_DEFAULT,
                minGroupLen : Int = 0,
                desc : Boolean? = null
        ) : VideosModel {
            return ViewModelProviders.of(fragment, Factory(context, group, minGroupLen, sort, desc)).get(VideosModel::class.java)
        }
    }
}
