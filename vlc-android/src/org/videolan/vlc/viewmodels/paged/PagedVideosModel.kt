package org.videolan.vlc.viewmodels.paged

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.media.getAll
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.launchChannelUpdate


@ExperimentalCoroutinesApi
class PagedVideosModel(
        context: Context,
        val folder : Folder?,
        customSort : Int,
        customDesc: Boolean?
) : MLPagedModel<MediaWrapper>(context), Medialibrary.MediaCb {
    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    override fun canSortByFileNameName() = true
    override fun canSortByDuration() = true
    override fun canSortByLastModified() = folder == null

     init {
         sort = if (customSort != Medialibrary.SORT_DEFAULT) customSort
         else Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
         desc = customDesc ?: Settings.getInstance(context).getBoolean(sortKey + "_desc", false)
         if (medialibrary.isStarted) {
             medialibrary.addMediaCb(this)
         }
     }

    //TODO Search in folder
    override fun getTotalCount() = if (filterQuery == null) when {
        folder !== null -> folder.mediaCount(Folder.TYPE_FOLDER_VIDEO)
        else -> medialibrary.videoCount
    } else when {
        folder !== null -> folder.searchTracksCount(filterQuery, Folder.TYPE_FOLDER_VIDEO)
        else -> medialibrary.getVideoCount(filterQuery)
    }

    override fun getPage(loadSize: Int, startposition: Int): Array<MediaWrapper> = if (filterQuery == null) when {
        folder !== null -> folder.media(Folder.TYPE_FOLDER_VIDEO, sort, desc, loadSize, startposition)
        else -> medialibrary.getPagedVideos(sort, desc, loadSize, startposition)
    } else when {
        folder !== null -> folder.searchTracks(filterQuery, Folder.TYPE_FOLDER_VIDEO, sort, desc, loadSize, startposition)
        else -> medialibrary.searchVideo(filterQuery, sort, desc, loadSize, startposition)
    }

    override fun getAll(): Array<MediaWrapper> = when {
        folder !== null -> folder.getAll(Folder.TYPE_FOLDER_VIDEO, sort, desc).toTypedArray()
        else -> medialibrary.videos
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onMedialibraryIdle() {
        super.onMedialibraryIdle()
        if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater) context.launchChannelUpdate()
    }

    class Factory(
            private val context: Context,
            private val folder : Folder?,
            private val sort : Int,
            private val desc : Boolean?
    ): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedVideosModel(context.applicationContext, folder, sort, desc) as T
        }
    }

    companion object {
        @JvmOverloads
        fun get(
                context: Context,
                fragment: Fragment,
                folder: Folder? = null,
                sort : Int = Medialibrary.SORT_DEFAULT,
                desc : Boolean? = null
        ) : PagedVideosModel {
            return ViewModelProviders.of(fragment, Factory(context, folder, sort, desc)).get(PagedVideosModel::class.java)
        }
    }
}