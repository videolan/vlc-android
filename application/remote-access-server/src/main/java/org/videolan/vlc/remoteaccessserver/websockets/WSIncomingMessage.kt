/*
 * ************************************************************************
 *  WSIncomingMessage.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.remoteaccessserver.websockets

data class WSIncomingMessage(
            val message: String,
            val id: Int?,
            val floatValue: Float? = null,
            val longValue: Long? = null,
            val stringValue: String? = null,
            val authTicket: String? = null
)

enum class IncomingMessageType(private val type: String, val controlRequired: Boolean = true) {
    HELLO("hello", false),
    PLAY("play"),
    PAUSE("pause"),
    PREVIOUS("previous"),
    NEXT("next"),
    PREVIOUS10("previous10"),
    NEXT10("next10" ),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    GET_VOLUME("get-volume", false),
    SET_VOLUME("set-volume"),
    SET_PROGRESS("set-progress"),
    PLAY_CHAPTER("play-chapter"),
    SPEED("speed"),
    SLEEP_TIMER("sleep-timer"),
    SLEEP_TIMER_WAIT("sleep-timer-wait"),
    SLEEP_TIMER_RESET("sleep-timer-reset"),
    ADD_BOOKMARK("add-bookmark"),
    DELETE_BOOKMARK("delete-bookmark"),
    RENAME_BOOKMARK("rename-bookmark"),
    PLAY_MEDIA("play-media"),
    DELETE_MEDIA("delete-media"),
    MOVE_MEDIA_BOTTOM("move-media-bottom"),
    MOVE_MEDIA_TOP("move-media-top"),
    REMOTE("remote" );

    override fun toString(): String = type

    companion object {
        fun fromString(type: String) = entries.firstOrNull { type == it.type }
    }
}