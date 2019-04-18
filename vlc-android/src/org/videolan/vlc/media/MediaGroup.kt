/*****************************************************************************
 * MediaGroup.java
 *
 * Copyright © 2013 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.media

import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.gui.helpers.BitmapUtil
import java.util.*

class MediaGroup private constructor(media: MediaWrapper, filename: Boolean) : MediaWrapper(media.uri, media.time, media.length, TYPE_GROUP, BitmapUtil.getPicture(media), if (filename) media.fileName else media.title, media.artist, media.genre, media.album, media.albumArtist, media.width, media.height, media.artworkURL, media.audioTrack, media.spuTrack, media.trackNumber, media.discNumber, media.lastModified, media.seen) {

    private val mMedias: MutableList<MediaWrapper>

    val displayTitle: String
        get() = title + "\u2026"

    val media: MediaWrapper
        get() = if (mMedias.size == 1) mMedias[0] else this

    val firstMedia: MediaWrapper
        get() = mMedias[0]

    val all: List<MediaWrapper>
        get() = mMedias

    init {
        mMedias = ArrayList()
        mMedias.add(media)
    }

    override fun getFileName(): String {
        return mTitle
    }

    fun add(media: MediaWrapper) {
        mMedias.add(media)
    }

    fun size(): Int {
        return mMedias.size
    }

    private fun merge(media: MediaWrapper, title: String) {
        mMedias.add(media)
        this.mTitle = title
    }

    companion object {

        val TAG = "VLC/MediaGroup"

        fun group(mediaList: Array<MediaWrapper>, minGroupLengthValue: Int, filename: Boolean): List<MediaGroup> {
            val groups = ArrayList<MediaGroup>()
            for (media in mediaList) insertInto(groups, media, minGroupLengthValue, filename)
            return groups
        }

        private fun insertInto(groups: ArrayList<MediaGroup>, media: MediaWrapper, minGroupLengthValue: Int, filename: Boolean) {
            for (mediaGroup in groups) {
                val group = mediaGroup.title.toLowerCase()
                var title = (if (filename) media.fileName else media.title).toLowerCase()

                //Handle titles starting with "The"
                val groupOffset = if (group.startsWith("the")) 4 else 0
                if (title.startsWith("the"))
                    title = title.substring(4)

                // find common prefix
                var commonLength = 0
                val groupTitle = group.substring(groupOffset)
                val minLength = Math.min(groupTitle.length, title.length)
                while (commonLength < minLength && groupTitle[commonLength] == title[commonLength])
                    ++commonLength

                if (commonLength >= minGroupLengthValue && minGroupLengthValue != 0) {
                    if (commonLength == group.length) {
                        // same prefix name, just add
                        mediaGroup.add(media)
                    } else {
                        // not the same prefix, but close : merge
                        mediaGroup.merge(media, mediaGroup.title.substring(0, commonLength + groupOffset))
                    }
                    return
                }
            }

            // does not match any group, so add one
            groups.add(MediaGroup(media, filename))
        }
    }
}
