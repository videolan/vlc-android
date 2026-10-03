package org.videolan.vlc.discourse

import android.content.Context
import org.videolan.tools.Settings

private const val KEY_PREFIX = "osho_api_discourse_playback_position."

internal class DiscoursePlaybackStore(context: Context) {
    private val settings = Settings.getInstance(context.applicationContext)

    fun position(audioId: String): Long? = settings.getLong(key(audioId), -1L).takeIf { it >= 0L }

    fun save(audioId: String, position: Long) {
        settings.edit().putLong(key(audioId), position.coerceAtLeast(0L)).apply()
    }

    fun clear(audioId: String) {
        settings.edit().remove(key(audioId)).apply()
    }

    private fun key(audioId: String) = "$KEY_PREFIX$audioId"
}
