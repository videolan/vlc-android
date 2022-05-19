/*****************************************************************************
 * MediaWrapperList.java
 *
 * Copyright © 2013-2015 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */
package org.videolan.vlc.media

import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.*

class MediaWrapperList {

    /* TODO: add locking */
    private val internalList = ArrayList<MediaWrapper>()
    private val eventListenerList = ArrayList<EventListener>()
    private var videoCount = 0

    val copy: MutableList<MediaWrapper>
        @Synchronized get() = ArrayList(internalList)

    val isAudioList: Boolean
        @Synchronized get() = videoCount == 0

    interface EventListener {
        fun onItemAdded(index: Int, mrl: String)
        fun onItemRemoved(index: Int, mrl: String)
        fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String)
    }

    @Synchronized
    fun add(media: MediaWrapper) {
        internalList.add(media)
        signalEventListeners(EVENT_ADDED, internalList.size - 1, -1, media.location)
        if (media.type == MediaWrapper.TYPE_VIDEO)
            ++videoCount
    }

    @Synchronized
    fun addEventListener(listener: EventListener) {
        if (!eventListenerList.contains(listener))
            eventListenerList.add(listener)
    }

    @Synchronized
    fun removeEventListener(listener: EventListener) {
        eventListenerList.remove(listener)
    }

    @Synchronized
    private fun signalEventListeners(event: Int, arg1: Int, arg2: Int, mrl: String) {
        for (listener in eventListenerList) {
            when (event) {
                EVENT_ADDED -> listener.onItemAdded(arg1, mrl)
                EVENT_REMOVED -> listener.onItemRemoved(arg1, mrl)
                EVENT_MOVED -> listener.onItemMoved(arg1, arg2, mrl)
            }
        }
    }

    /**
     * Clear the media list. (remove all media)
     */
    @Synchronized
    fun clear() {
        // Signal to observers of media being deleted.
        for (i in internalList.indices)
            signalEventListeners(EVENT_REMOVED, i, -1, internalList[i].location)
        internalList.clear()
        videoCount = 0
    }

    @Synchronized
    private fun isValid(position: Int): Boolean {
        return position >= 0 && position < internalList.size
    }

    @Synchronized
    fun insert(position: Int, media: MediaWrapper) {
        if (position < 0) return
        internalList.add(position.coerceAtMost(internalList.size), media)
        signalEventListeners(EVENT_ADDED, position, -1, media.location)
        if (media.type == MediaWrapper.TYPE_VIDEO)
            ++videoCount
    }

    /**
     * Move a media from one position to another
     *
     * @param startPosition start position
     * @param endPosition end position
     * @throws IndexOutOfBoundsException
     */
    @Synchronized
    fun move(startPosition: Int, endPosition: Int) {
        if (!(isValid(startPosition)
                        && endPosition >= 0 && endPosition <= internalList.size))
            throw IndexOutOfBoundsException("Indexes out of range")

        val toMove = internalList[startPosition]
        internalList.removeAt(startPosition)
        if (startPosition >= endPosition)
            internalList.add(endPosition, toMove)
        else
            internalList.add(endPosition - 1, toMove)
        signalEventListeners(EVENT_MOVED, startPosition, endPosition, toMove.location)
    }

    @Synchronized
    fun remove(position: Int) {
        if (!isValid(position)) return
        if (internalList[position].type == MediaWrapper.TYPE_VIDEO)
            --videoCount
        val uri = internalList[position].location
        internalList.removeAt(position)
        signalEventListeners(EVENT_REMOVED, position, -1, uri)
    }

    @Synchronized
    fun remove(location: String) {
        var i = 0
        while (i < internalList.size) {
            val uri = internalList[i].location
            if (uri == location) {
                if (internalList[i].type == MediaWrapper.TYPE_VIDEO)
                    --videoCount
                internalList.removeAt(i)
                signalEventListeners(EVENT_REMOVED, i, -1, uri)
                i--
            }
            ++i
        }
    }

    @Synchronized
    fun size(): Int {
        return internalList.size
    }

    @Synchronized
    fun getMedia(position: Int): MediaWrapper? {
        return if (isValid(position)) internalList[position] else null
    }

    @Synchronized
    fun replaceWith(list: List<MediaWrapper>) {
        internalList.clear()
        internalList.addAll(list)
    }

    @Synchronized
    fun map(list: List<MediaWrapper>) {
        internalList.addAll(list)
    }

    /**
     * @param position The index of the media in the list
     * @return null if not found
     */
    @Synchronized
    private fun getMRL(position: Int): String? {
        return if (!isValid(position)) null else internalList[position].location
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("LibVLC Media List: {")
        for (i in 0 until size()) {
            sb.append(i.toString())
            sb.append(": ")
            sb.append(getMRL(i))
            sb.append(", ")
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        private const val TAG = "VLC/MediaWrapperList"

        private const val EVENT_ADDED = 0
        private const val EVENT_REMOVED = 1
        private const val EVENT_MOVED = 2
    }
}
