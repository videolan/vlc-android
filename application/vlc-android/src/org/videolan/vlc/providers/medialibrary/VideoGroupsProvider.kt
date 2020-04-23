package org.videolan.vlc.providers.medialibrary

import android.content.Context
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.viewmodels.SortableModel


class VideoGroupsProvider(context: Context, model: SortableModel) : MedialibraryProvider<MediaLibraryItem>(context, model) {
    override fun getAll() : Array<MediaLibraryItem> = medialibrary.getVideoGroups(sort, desc, getTotalCount(), 0).extractSingles()

    override fun getTotalCount() = medialibrary.videoGroupsCount

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaLibraryItem> = medialibrary.getVideoGroups(sort, desc, loadSize, startposition).extractSingles().also { completeHeaders(it, startposition) }
}

private fun Array<VideoGroup>.extractSingles() = map {
    if (it.mediaCount() == 1 || it.mediaCount() == 0) it.media(Medialibrary.SORT_DEFAULT, false, 1, 0)[0] else it
}.toTypedArray()