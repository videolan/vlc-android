package org.videolan.vlc.providers.medialibrary

import android.content.Context
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.vlc.viewmodels.SortableModel


class VideoGroupsProvider(context: Context, scope: SortableModel) : MedialibraryProvider<AbstractVideoGroup>(context, scope) {
    override fun getAll() : Array<AbstractVideoGroup> = medialibrary.getVideoGroups(sort, desc, getTotalCount(), 0)

    override fun getTotalCount() = medialibrary.videoGroupsCount

    override fun getPage(loadSize: Int, startposition: Int) : Array<AbstractVideoGroup> = medialibrary.getVideoGroups(sort, desc, loadSize, startposition).also { completeHeaders(it, startposition) }

}