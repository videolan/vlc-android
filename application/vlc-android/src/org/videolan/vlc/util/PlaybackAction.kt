/**
 * **************************************************************************
 * PlaybackAction.kt
 * ****************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 *
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
 * ***************************************************************************
 */
package org.videolan.vlc.util

import android.support.v4.media.session.PlaybackStateCompat

@Suppress("unused")
enum class PlaybackAction(private val capability: Long) : Flag {
    ACTION_STOP(PlaybackStateCompat.ACTION_STOP),
    ACTION_PAUSE(PlaybackStateCompat.ACTION_PAUSE),
    ACTION_PLAY(PlaybackStateCompat.ACTION_PLAY),
    ACTION_REWIND(PlaybackStateCompat.ACTION_REWIND),
    ACTION_SKIP_TO_PREVIOUS(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS),
    ACTION_SKIP_TO_NEXT(PlaybackStateCompat.ACTION_SKIP_TO_NEXT),
    ACTION_FAST_FORWARD(PlaybackStateCompat.ACTION_FAST_FORWARD),
    ACTION_SET_RATING(PlaybackStateCompat.ACTION_SET_RATING),
    ACTION_SEEK_TO(PlaybackStateCompat.ACTION_SEEK_TO),
    ACTION_PLAY_PAUSE(PlaybackStateCompat.ACTION_PLAY_PAUSE),
    ACTION_PLAY_FROM_MEDIA_ID(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID),
    ACTION_PLAY_FROM_SEARCH(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH),
    ACTION_SKIP_TO_QUEUE_ITEM(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM),
    ACTION_PLAY_FROM_URI(PlaybackStateCompat.ACTION_PLAY_FROM_URI),
    ACTION_PREPARE(PlaybackStateCompat.ACTION_PREPARE),
    ACTION_PREPARE_FROM_MEDIA_ID(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID),
    ACTION_PREPARE_FROM_SEARCH(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH),
    ACTION_PREPARE_FROM_URI(PlaybackStateCompat.ACTION_PREPARE_FROM_URI),
    ACTION_SET_REPEAT_MODE(PlaybackStateCompat.ACTION_SET_REPEAT_MODE),

    /* ACTION_SET_SHUFFLE_MODE_ENABLED replaced with ACTION_SET_SHUFFLE_MODE */
    ACTION_SET_CAPTIONING_ENABLED(PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED),
    ACTION_SET_SHUFFLE_MODE(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE),
    ACTION_SET_PLAYBACK_SPEED(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED);

    override fun toLong() = this.capability

    companion object {
        fun createBaseActions() = FlagSet(PlaybackAction::class.java).apply {
            addAll(ACTION_PLAY_PAUSE, ACTION_PLAY_FROM_MEDIA_ID, ACTION_PLAY_FROM_SEARCH, ACTION_SKIP_TO_QUEUE_ITEM, ACTION_PLAY_FROM_URI)
        }

        fun createActivePlaybackActions() = createBaseActions().apply {
            addAll(ACTION_REWIND, ACTION_FAST_FORWARD, ACTION_SEEK_TO, ACTION_SET_PLAYBACK_SPEED)
        }
    }
}
