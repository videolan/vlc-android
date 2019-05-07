/*****************************************************************************
 * PagedVideosModel.kt
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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.launchChannelUpdate


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PagedVideosModel(
        context: Context,
        val folder : Folder?,
        customSort : Int,
        customDesc: Boolean?
) : MLPagedModel<MediaWrapper>(context), Medialibrary.MediaCb {
    override val provider = VideosProvider(folder, context, this)

     init {
         sort = if (customSort != Medialibrary.SORT_DEFAULT) customSort
         else Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
         desc = customDesc ?: Settings.getInstance(context).getBoolean(sortKey + "_desc", false)
         if (medialibrary.isStarted) {
             medialibrary.addMediaCb(this)
         }
     }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onMedialibraryIdle() {
        super.onMedialibraryIdle()
        if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater) context.launchChannelUpdate()
    }

    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    class Factory(
            private val context: Context,
            private val folder : Folder?,
            private val sort: Int = Medialibrary.SORT_DEFAULT,
            private val desc: Boolean? = null
    ): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedVideosModel(context.applicationContext, folder, sort, desc) as T
        }
    }

    companion object {
        @JvmOverloads
        fun get(
                fragment: Fragment,
                folder: Folder? = null,
                sort : Int = Medialibrary.SORT_DEFAULT,
                desc : Boolean? = null
        ) : PagedVideosModel {
            return ViewModelProviders.of(fragment.requireActivity(), Factory(fragment.requireContext(), folder, sort, desc)).get(PagedVideosModel::class.java)
        }
    }
}