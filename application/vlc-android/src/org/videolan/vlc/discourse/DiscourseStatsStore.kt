package org.videolan.vlc.discourse

import android.content.Context

private const val STORE_NAME = "osho_api_stats"
private const val DISCOURSE_STATS = "discourses"
private const val AUDIO_STATS = "audios"
private const val ENTRY_SEPARATOR = "|"
private const val RETENTION_MS = 24L * 60L * 60L * 1000L

class DiscourseStatsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun shouldSend(discourseId: String, audioId: String, now: Long = System.currentTimeMillis()): Boolean {
        cleanup(now)
        return !contains(DISCOURSE_STATS, discourseId, now) && !contains(AUDIO_STATS, audioId, now)
    }

    @Synchronized
    fun markSent(discourseId: String, audioId: String, now: Long = System.currentTimeMillis()) {
        cleanup(now)
        preferences.edit()
            .putStringSet(DISCOURSE_STATS, entries(DISCOURSE_STATS) + entry(discourseId, now))
            .putStringSet(AUDIO_STATS, entries(AUDIO_STATS) + entry(audioId, now))
            .apply()
    }

    @Synchronized
    fun cleanup(now: Long = System.currentTimeMillis()) {
        val cutoff = now - RETENTION_MS
        preferences.edit()
            .putStringSet(DISCOURSE_STATS, entries(DISCOURSE_STATS).filter { timestamp(it) > cutoff }.toSet())
            .putStringSet(AUDIO_STATS, entries(AUDIO_STATS).filter { timestamp(it) > cutoff }.toSet())
            .apply()
    }

    private fun contains(key: String, id: String, now: Long) = entries(key).any {
        it.substringBefore(ENTRY_SEPARATOR) == id && timestamp(it) > now - RETENTION_MS
    }

    private fun entries(key: String) = preferences.getStringSet(key, emptySet()).orEmpty()

    private fun entry(id: String, timestamp: Long) = "$id$ENTRY_SEPARATOR$timestamp"

    private fun timestamp(value: String) = value.substringAfter(ENTRY_SEPARATOR, "0").toLongOrNull() ?: 0L
}
