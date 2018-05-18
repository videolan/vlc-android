package org.videolan.vlc.util

import android.text.TextUtils
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.interfaces.MediaUpdatedCb
import org.videolan.medialibrary.media.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.viewmodels.BaseModel

object ModelsHelper {

    fun generateSections(sort: Int, items: List<MediaLibraryItem>) : MutableList<MediaLibraryItem> {
        val array = splitList(sort, items)
        val datalist = mutableListOf<MediaLibraryItem>()
        for ((key, list) in array) {
            datalist.add(DummyItem(key))
            datalist.addAll(list)
        }
        return datalist
    }

    internal fun splitList(sort: Int, items: Collection<MediaLibraryItem>) : Map<String, List<MediaLibraryItem>> {
        val array = mutableMapOf<String, MutableList<MediaLibraryItem>>()
        when (sort) {
            Medialibrary.SORT_DEFAULT,
            Medialibrary.SORT_FILENAME,
            Medialibrary.SORT_ALPHA -> {
                var currentLetter: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val title = item.title
                    val letter = if (title.isEmpty() || !Character.isLetter(title[0]) || ModelsHelper.isSpecialItem(item)) "#" else title.substring(0, 1).toUpperCase()
                    if (currentLetter === null || !TextUtils.equals(currentLetter, letter)) {
                        currentLetter = letter
                        array[letter] = mutableListOf()
                    }
                    array[letter]?.add(item)
                }
            }
            Medialibrary.SORT_DURATION -> {
                var currentLengthCategory: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val length = ModelsHelper.getLength(item)
                    val lengthCategory = ModelsHelper.lengthToCategory(length)
                    if (currentLengthCategory == null || !TextUtils.equals(currentLengthCategory, lengthCategory)) {
                        currentLengthCategory = lengthCategory
                        array[currentLengthCategory] = mutableListOf()
                    }
                    array[currentLengthCategory]?.add(item)
                }
            }
            Medialibrary.SORT_RELEASEDATE -> {
                var currentYear: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val year = ModelsHelper.getYear(item)
                    if (currentYear === null || !TextUtils.equals(currentYear, year)) {
                        currentYear = year
                        array[currentYear] = mutableListOf()
                    }
                    array[currentYear]?.add(item)
                }
            }
            Medialibrary.SORT_ARTIST -> {
                var currentArtist: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val artist = (item as MediaWrapper).artist ?: ""
                    if (currentArtist === null || !TextUtils.equals(currentArtist, artist)) {
                        currentArtist = artist
                        array[currentArtist] = mutableListOf()
                    }
                    array[currentArtist]?.add(item)
                }
            }
            Medialibrary.SORT_ALBUM -> {
                var currentAlbum: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val album = (item as MediaWrapper).album ?: ""
                    if (currentAlbum === null || !TextUtils.equals(currentAlbum, album)) {
                        currentAlbum = album
                        array[currentAlbum] = mutableListOf()
                    }
                    array[currentAlbum]?.add(item)
                }
            }
        }
        return array
    }

    private fun isSpecialItem(item: MediaLibraryItem) = item.itemType == MediaLibraryItem.TYPE_ARTIST
            && (item.id == 1L || item.id == 2L) || item.itemType == MediaLibraryItem.TYPE_ALBUM
            && item.title == Album.SpecialRes.UNKNOWN_ALBUM

    private fun getLength(media: MediaLibraryItem): Int {
        return when {
            media.itemType == MediaLibraryItem.TYPE_ALBUM -> (media as Album).duration
            media.itemType == MediaLibraryItem.TYPE_MEDIA -> (media as MediaWrapper).length.toInt()
            else -> 0
        }
    }

    private fun lengthToCategory(length: Int): String {
        val value: Int
        if (length == 0) return "-"
        if (length < 60000) return "< 1 min"
        if (length < 600000) {
            value = Math.floor((length / 60000).toDouble()).toInt()
            return value.toString() + " - " + (value + 1).toString() + " min"
        }
        return if (length < 3600000) {
            value = (10 * Math.floor((length / 600000).toDouble())).toInt()
            value.toString() + " - " + (value + 10).toString() + " min"
        } else {
            value = Math.floor((length / 3600000).toDouble()).toInt()
            value.toString() + " - " + (value + 1).toString() + " h"
        }
    }

    private fun getYear(media: MediaLibraryItem): String {
        return when (media.itemType) {
            MediaLibraryItem.TYPE_ALBUM -> if ((media as Album).releaseYear == 0) "-" else media.releaseYear.toString()
            MediaLibraryItem.TYPE_MEDIA -> if ((media as MediaWrapper).date == null) "-" else media.date
            else -> "-"
        }
    }

    fun getTracksCount(media: MediaLibraryItem): Int {
        return when (media.itemType) {
            MediaLibraryItem.TYPE_ALBUM -> (media as Album).tracksCount
            MediaLibraryItem.TYPE_PLAYLIST -> (media as Playlist).tracksCount
            else -> 0
        }
    }
}

object EmptyMLCallbacks : MediaAddedCb, MediaUpdatedCb, Medialibrary.ArtistsAddedCb, Medialibrary.ArtistsModifiedCb, Medialibrary.AlbumsAddedCb, Medialibrary.AlbumsModifiedCb {
    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {}
    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {}
    override fun onArtistsAdded() {}
    override fun onArtistsModified() {}
    override fun onAlbumsAdded() {}
    override fun onAlbumsModified() {}
}

object EmptyPBSCallback : PlaybackService.Callback {
    override fun update() {}
    override fun onMediaEvent(event: Media.Event) {}
    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {}
}

interface RefreshModel {
    fun refresh() : Boolean
}

fun <T : MediaLibraryItem> BaseModel<T>.canSortBy(sort: Int) = when(sort) {
    Medialibrary.SORT_DEFAULT -> true
    Medialibrary.SORT_ALPHA -> canSortByName()
    Medialibrary.SORT_FILENAME -> canSortByFileNameName()
    Medialibrary.SORT_DURATION -> canSortByDuration()
    Medialibrary.SORT_INSERTIONDATE -> canSortByInsertionDate()
    Medialibrary.SORT_LASTMODIFICATIONDATE -> canSortByLastModified()
    Medialibrary.SORT_RELEASEDATE -> canSortByReleaseDate()
    Medialibrary.SORT_FILESIZE -> canSortByFileSize()
    Medialibrary.SORT_ARTIST -> canSortByArtist()
    Medialibrary.SORT_ALBUM -> canSortByAlbum()
    Medialibrary.SORT_PLAYCOUNT -> canSortByPlayCount()
    else -> false
}