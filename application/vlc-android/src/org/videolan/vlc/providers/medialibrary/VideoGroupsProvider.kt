package org.videolan.vlc.providers.medialibrary

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.Settings
import org.videolan.vlc.util.isSchemeFile
import org.videolan.vlc.viewmodels.SortableModel

class VideoGroupsProvider(context: Context, model: SortableModel) : MedialibraryProvider<MediaLibraryItem>(context, model) {
    override fun canSortByDuration() = true
    override fun canSortByInsertionDate() = true
    override fun canSortByLastModified() = true
    override fun canSortByMediaNumber() = true

    override fun getAll() : Array<VideoGroup> = medialibrary.getVideoGroups(sort, desc, Settings.includeMissing, onlyFavorites, getTotalCount(), 0)

    override fun getTotalCount() = medialibrary.getVideoGroupsCount(model.filterQuery)

    override fun getPage(loadSize: Int, startposition: Int): Array<MediaLibraryItem> {
        val medias = if (model.filterQuery.isNullOrEmpty()) {
            medialibrary.getVideoGroups(sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        } else {
            medialibrary.searchVideoGroups(model.filterQuery, sort, desc, Settings.includeMissing, onlyFavorites, loadSize, startposition)
        }.sanitizeGroups().also { if (Settings.showTvUi) completeHeaders(it, startposition) }
        model.viewModelScope.launch { completeHeaders(medias, startposition) }
        return medias
    }
}

/**
 * Extracts groups containing only one video and replace them by the video.
 *
 * Also checks if the group has network media and sets the [VideoGroup.isNetwork] field accordingly
 *
 * @return a list of [MediaLibraryItem] containing the groups and the lonely medias
 */
fun Array<VideoGroup>.sanitizeGroups() = map { videoGroup ->
    if (videoGroup.mediaCount() == 1) {
        val video = videoGroup.media(Medialibrary.SORT_DEFAULT, false, true, false, 1, 0).getOrNull(0)
        if (video != null) {
            video
        } else {
            checkIsNetwork(videoGroup)
            videoGroup
        }
    } else {
        checkIsNetwork(videoGroup)
        videoGroup
    }
}.toTypedArray()

/**
 * Update the [VideoGroup.isNetwork] flag if needed (at least one media is a network one)
 */
private fun checkIsNetwork(videoGroup: VideoGroup) {
    videoGroup.media(Medialibrary.SORT_DEFAULT, false, true, false, videoGroup.mediaCount(), 0).filterNotNull().forEach {
        if (it.uri?.scheme?.isSchemeFile() == false) {
            videoGroup.isNetwork = true
            return@forEach
        }
    }
}