package org.videolan.vlc.discourse

import androidx.core.content.edit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.videolan.vlc.BaseTest

class DiscourseStatsStoreTest : BaseTest() {
    private lateinit var store: DiscourseStatsStore

    @Before
    fun setUpStore() {
        context.getSharedPreferences("osho_api_stats", 0).edit { clear() }
        store = DiscourseStatsStore(context)
    }

    @Test
    fun suppressesRecentDiscourseOrAudio() {
        val now = 1_000_000L
        assertTrue(store.shouldSend("discourse", "audio", now))

        store.markSent("discourse", "audio", now)

        assertFalse(store.shouldSend("discourse", "other-audio", now + 1))
        assertFalse(store.shouldSend("other-discourse", "audio", now + 1))
        assertTrue(store.shouldSend("other-discourse", "other-audio", now + 1))
    }

    @Test
    fun expiresBothEntityRecordsAfter24Hours() {
        val now = 1_000_000L
        store.markSent("discourse", "audio", now)

        assertFalse(store.shouldSend("discourse", "audio", now + 24L * 60L * 60L * 1000L - 1L))
        assertTrue(store.shouldSend("discourse", "audio", now + 24L * 60L * 60L * 1000L))
    }
}
