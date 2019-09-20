package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.isStarted
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.videogroups.VideoGroupsFragment
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel


class VideogroupsViewModel(context: Context) : MedialibraryViewModel(context) {
    val provider = VideoGroupsProvider(context, this)
    override val providers: Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    fun play(position: Int) = launch {
        val list = withContext(Dispatchers.IO) { provider.pagedList.value?.get(position)?.getAll() }
        list?.let { MediaUtils.openList(context, it, 0) }
    }

    fun append(position: Int) = launch {
        val list = withContext(Dispatchers.IO) { provider.pagedList.value?.get(position)?.getAll() }
        list?.let { MediaUtils.appendMedia(context, it) }
    }

    fun addToPlaylist(activity: FragmentActivity, selection: List<AbstractVideoGroup>) = launch {
        val list = withContext(Dispatchers.Default) { selection.getAll() }
        if (activity.isStarted()) UiTools.addToPlaylist(activity, list)
    }

    fun addToPlaylist(activity: FragmentActivity, position: Int) = launch {
        provider.pagedList.value?.get(position)?.let {
                val list = withContext(Dispatchers.IO) { it.getAll() }
                if (activity.isStarted()) UiTools.addToPlaylist(activity, list)
        }
    }

    fun playSelection(selection: List<AbstractVideoGroup>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun appendSelection(selection: List<AbstractVideoGroup>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    class Factory(val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideogroupsViewModel(context.applicationContext) as T
        }
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal fun VideoGroupsFragment.getViewModel() = ViewModelProviders.of(requireActivity(), VideogroupsViewModel.Factory(requireContext())).get(VideogroupsViewModel::class.java)
