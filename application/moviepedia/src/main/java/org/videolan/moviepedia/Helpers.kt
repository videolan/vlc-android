package org.videolan.moviepedia

import android.content.Context
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.moviepedia.database.models.MediaMetadata
import org.videolan.moviepedia.database.models.getYear
import java.util.*


fun getHeaderMoviepedia(context: Context?, sort: Int, item: MediaMetadata?, aboveItem: MediaMetadata?) = if (context !== null && item != null) when (sort) {
    Medialibrary.SORT_DEFAULT,
    Medialibrary.SORT_FILENAME,
    Medialibrary.SORT_ALPHA -> {
        val letter = if (item.title.isEmpty() || !Character.isLetter(item.title[0])) "#" else item.title.substring(0, 1).uppercase(Locale.getDefault())
        if (aboveItem == null) letter
        else {
            val previous = if (aboveItem.title.isEmpty() || !Character.isLetter(aboveItem.title[0])) "#" else aboveItem.title.substring(0, 1).uppercase(Locale.getDefault())
            letter.takeIf { it != previous }
        }
    }
//        SORT_DURATION -> {
//            val length = item.getLength()
//            val lengthCategory = length.lengthToCategory()
//            if (aboveItem == null) lengthCategory
//            else {
//                val previous = aboveItem.getLength().lengthToCategory()
//                lengthCategory.takeIf { it != previous }
//            }
//        }
    Medialibrary.SORT_RELEASEDATE -> {
        val year = item.getYear()
        if (aboveItem == null) year
        else {
            val previous = aboveItem.getYear()
            year.takeIf { it != previous }
        }
    }
//        SORT_LASTMODIFICATIONDATE -> {
//            val timestamp = (item as MediaWrapper).lastModified
//            val category = getTimeCategory(timestamp)
//            if (aboveItem == null) getTimeCategoryString(context, category)
//            else {
//                val prevCat = getTimeCategory((aboveItem as MediaWrapper).lastModified)
//                if (prevCat != category) getTimeCategoryString(context, category) else null
//            }
//        }
//        SORT_ARTIST -> {
//            val artist = (item as MediaWrapper).artist ?: ""
//            if (aboveItem == null) artist
//            else {
//                val previous = (aboveItem as MediaWrapper).artist ?: ""
//                artist.takeIf { it != previous }
//            }
//        }
//        SORT_ALBUM -> {
//            val album = (item as MediaWrapper).album ?: ""
//            if (aboveItem == null) album
//            else {
//                val previous = (aboveItem as MediaWrapper).album ?: ""
//                album.takeIf { it != previous }
//            }
//        }
    else -> null
} else null