package org.videolan.vlc.util

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.media.MediaUtils

open class FilterDelegate<T : MediaLibraryItem>(protected val dataset: MutableLiveData<out List<T>>) {
    private var sourceSet: List<T>? = null

    protected fun initSource() : List<T>? {
        if (sourceSet === null) sourceSet = (dataset.value)
        return sourceSet
    }

    suspend fun filter(charSequence: CharSequence?) = publish(filteringJob(charSequence))

    protected open suspend fun filteringJob(charSequence: CharSequence?) : MutableList<T>? {
        if (charSequence !== null) initSource()?.let {
            return withContext(Dispatchers.Default) { mutableListOf<T>().apply {
                val queryStrings = charSequence.trim().toString().split(" ").filter { it.length > 2 }
                for (item in it) for (query in queryStrings)
                    if (item.title.contains(query, true)) {
                        this.add(item)
                        break
                    }
                }
            }
        }
        return null
    }

    private fun publish(list: MutableList<T>?) {
        sourceSet?.let {
            if (list !== null)
                dataset.value = list
            else {
                dataset.value = it
                sourceSet = null
            }
        }
    }
}

class PlaylistFilterDelegate(dataset: MutableLiveData<out List<MediaWrapper>>) : FilterDelegate<MediaWrapper>(dataset) {

    override suspend fun filteringJob(charSequence: CharSequence?): MutableList<MediaWrapper>? {
        if (charSequence !== null) initSource()?.let { list ->
            return withContext(Dispatchers.Default) { mutableListOf<MediaWrapper>().apply {
                val queryStrings = charSequence.trim().toString().split(" ").asSequence().filter { it.length > 2 }.map { it.toLowerCase() }.toList()
                for (media in list) {
                    val title = MediaUtils.getMediaTitle(media).toLowerCase()
                    val location = media.location.toLowerCase()
                    val artist = MediaUtils.getMediaArtist(VLCApplication.getAppContext(), media).toLowerCase()
                    val albumArtist = MediaUtils.getMediaAlbumArtist(VLCApplication.getAppContext(), media).toLowerCase()
                    val album = MediaUtils.getMediaAlbum(VLCApplication.getAppContext(), media).toLowerCase()
                    val genre = MediaUtils.getMediaGenre(VLCApplication.getAppContext(), media).toLowerCase()
                    for (queryString in queryStrings) {
                        if (title.contains(queryString) ||
                                location.contains(queryString) ||
                                artist.contains(queryString) ||
                                albumArtist.contains(queryString) ||
                                album.contains(queryString) ||
                                genre.contains(queryString)) {
                            this.add(media)
                            break
                        }
                    }
                }
            }
            }
        }
        return null
    }
}