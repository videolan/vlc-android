package org.videolan.vlc.util

import android.content.Context
import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.Medialibrary.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.*
import org.videolan.vlc.PlaybackService
import kotlin.math.floor


object ModelsHelper {

    suspend fun generateSections(sort: Int, items: List<MediaLibraryItem>) = withContext(Dispatchers.IO) {
        val array = splitList(sort, items)
        val datalist = mutableListOf<MediaLibraryItem>()
        for ((key, list) in array) {
            datalist.add(DummyItem(key))
            datalist.addAll(list)
        }
        datalist
    }

    internal suspend fun splitList(sort: Int, items: Collection<MediaLibraryItem>) = withContext(Dispatchers.IO) {
        val array = mutableMapOf<String, MutableList<MediaLibraryItem>>()
        when (sort) {
            SORT_DEFAULT,
            SORT_FILENAME,
            SORT_ALPHA -> {
                var currentLetter: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val title = item.title
                    val letter = if (title.isEmpty() || !Character.isLetter(title[0]) || item.isSpecialItem()) "#" else title.substring(0, 1).toUpperCase()
                    if (currentLetter === null || !TextUtils.equals(currentLetter, letter)) {
                        currentLetter = letter
                        if (array[letter].isNullOrEmpty()) array[letter] = mutableListOf()
                    }
                    array[letter]?.add(item)
                }
            }
            SORT_DURATION -> {
                var currentLengthCategory: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val lengthCategory = item.getLength().lengthToCategory()
                    if (currentLengthCategory == null || !TextUtils.equals(currentLengthCategory, lengthCategory)) {
                        currentLengthCategory = lengthCategory
                        if (array[currentLengthCategory].isNullOrEmpty()) array[currentLengthCategory] = mutableListOf()
                    }
                    array[currentLengthCategory]?.add(item)
                }
            }
            SORT_RELEASEDATE -> {
                var currentYear: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val year = item.getYear()
                    if (currentYear === null || !TextUtils.equals(currentYear, year)) {
                        currentYear = year
                        if (array[currentYear].isNullOrEmpty()) array[currentYear] = mutableListOf()
                    }
                    array[currentYear]?.add(item)
                }
            }
            SORT_ARTIST -> {
                var currentArtist: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val artist = (item as MediaWrapper).artist ?: ""
                    if (currentArtist === null || !TextUtils.equals(currentArtist, artist)) {
                        currentArtist = artist
                        if (array[currentArtist].isNullOrEmpty()) array[currentArtist] = mutableListOf()
                    }
                    array[currentArtist]?.add(item)
                }
            }
            SORT_ALBUM -> {
                var currentAlbum: String? = null
                for (item in items) {
                    if (item.itemType == MediaLibraryItem.TYPE_DUMMY) continue
                    val album = (item as MediaWrapper).album ?: ""
                    if (currentAlbum === null || !TextUtils.equals(currentAlbum, album)) {
                        currentAlbum = album
                        if (array[currentAlbum].isNullOrEmpty()) array[currentAlbum] = mutableListOf()
                    }
                    array[currentAlbum]?.add(item)
                }
            }
        }
        if (sort == SORT_DEFAULT || sort == SORT_FILENAME || sort == SORT_ALPHA)
            array.toSortedMap()
        else array
    }

    fun MediaLibraryItem.getFirstLetter(): String {
        return if (title.isEmpty() || !Character.isLetter(title[0]) || isSpecialItem()) "#" else title.substring(0, 1).toUpperCase()
    }

    private fun MediaLibraryItem.getDiscNumber(): String? = if (this is MediaWrapper && this.discNumber != 0) "Disc ${this.discNumber}" else null

    fun getHeader(context: Context?, sort: Int, item: MediaLibraryItem?, aboveItem: MediaLibraryItem?) = if (context !== null && item != null) when (sort) {
        SORT_DEFAULT,
        SORT_ALPHA -> {
            val letter = if (item.title.isEmpty() || !Character.isLetter(item.title[0]) || item.isSpecialItem()) "#" else item.title.substring(0, 1).toUpperCase()
            if (aboveItem == null) letter
            else {
                val previous = if (aboveItem.title.isEmpty() || !Character.isLetter(aboveItem.title[0]) || aboveItem.isSpecialItem()) "#" else aboveItem.title.substring(0, 1).toUpperCase()
                letter.takeIf { it != previous }
            }
        }
        TrackId -> {
            val disc = item.getDiscNumber()
            if (aboveItem == null) disc
            else {
                val previousDisc = aboveItem.getDiscNumber()
                disc.takeIf { it != previousDisc }
            }
        }
        SORT_DURATION -> {
            val length = item.getLength()
            val lengthCategory = length.lengthToCategory()
            if (aboveItem == null) lengthCategory
            else {
                val previous = aboveItem.getLength().lengthToCategory()
                lengthCategory.takeIf { it != previous }
            }
        }
        SORT_RELEASEDATE -> {
            val year = item.getYear()
            if (aboveItem == null) year
            else {
                val previous = aboveItem.getYear()
                year.takeIf { it != previous }
            }
        }
        SORT_LASTMODIFICATIONDATE -> {
            val timestamp = (item as MediaWrapper).lastModified
            val category = getTimeCategory(timestamp)
            if (aboveItem == null) getTimeCategoryString(context, category)
            else {
                val prevCat = getTimeCategory((aboveItem as MediaWrapper).lastModified)
                if (prevCat != category) getTimeCategoryString(context, category) else null
            }
        }
        SORT_ARTIST -> {
            val artist = (item as MediaWrapper).artist ?: ""
            if (aboveItem == null) artist
            else {
                val previous = (aboveItem as MediaWrapper).artist ?: ""
                artist.takeIf { it != previous }
            }
        }
        SORT_ALBUM -> {
            val album = (item as MediaWrapper).album ?: ""
            if (aboveItem == null) album
            else {
                val previous = (aboveItem as MediaWrapper).album ?: ""
                album.takeIf { it != previous }
            }
        }
        SORT_FILENAME -> {
            val title = FileUtils.getFileNameFromPath((item as? MediaWrapper)?.uri.toString())
            val aboveTitle = FileUtils.getFileNameFromPath((aboveItem as? MediaWrapper)?.uri.toString())
            val letter = if (title.isEmpty() || !Character.isLetter(title[0]) || item.isSpecialItem()) "#" else title.substring(0, 1).toUpperCase()
            if (aboveItem == null) letter
            else {
                val previous = if (aboveTitle.isEmpty() || !Character.isLetter(aboveTitle[0]) || aboveItem.isSpecialItem()) "#" else aboveTitle.substring(0, 1).toUpperCase()
                letter.takeIf { it != previous }
            }
        }
        else -> null
    } else null
}

fun Long.lengthToCategory(): String {
    val value: Int
    if (this == 0L) return "-"
    if (this < 60000) return "< 1 min"
    if (this < 600000) {
        value = floor((this / 60000).toDouble()).toInt()
        return "$value - ${(value + 1)} min"
    }
    return if (this < 3600000) {
        value = (10 * floor((this / 600000).toDouble())).toInt()
        "$value - ${(value + 10)} min"
    } else {
        value = floor((this / 3600000).toDouble()).toInt()
        "$value - ${(value + 1)} h"
    }
}

object EmptyPBSCallback : PlaybackService.Callback {
    override fun update() {}
    override fun onMediaEvent(event: IMedia.Event) {}
    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {}
}

interface RefreshModel {
    fun refresh()
}

interface SortModule {
    fun sort(sort: Int)
    fun canSortByName() = true
    fun canSortByFileNameName() = false
    fun canSortByDuration() = false
    fun canSortByInsertionDate() = false
    fun canSortByLastModified() = false
    fun canSortByReleaseDate() = false
    fun canSortByFileSize() = false
    fun canSortByArtist() = false
    fun canSortByAlbum ()= false
    fun canSortByPlayCount() = false
    fun canSortByTrackId() = false
    fun canSortByMediaNumber() = false
    fun canSortBy(sort: Int) = when (sort) {
        SORT_DEFAULT -> true
        SORT_ALPHA -> canSortByName()
        SORT_FILENAME -> canSortByFileNameName()
        SORT_DURATION -> canSortByDuration()
        SORT_INSERTIONDATE -> canSortByInsertionDate()
        SORT_LASTMODIFICATIONDATE -> canSortByLastModified()
        SORT_RELEASEDATE -> canSortByReleaseDate()
        SORT_FILESIZE -> canSortByFileSize()
        SORT_ARTIST -> canSortByArtist()
        SORT_ALBUM -> canSortByAlbum()
        SORT_PLAYCOUNT -> canSortByPlayCount()
        TrackId -> canSortByTrackId()
        NbMedia -> canSortByMediaNumber()
        else -> false
    }
}