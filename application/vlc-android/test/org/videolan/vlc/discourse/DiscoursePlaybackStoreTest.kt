package org.videolan.vlc.discourse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.videolan.vlc.BaseTest

class DiscoursePlaybackStoreTest : BaseTest() {
    private lateinit var store: DiscoursePlaybackStore

    @Before
    fun setUpStore() {
        store = DiscoursePlaybackStore(context)
        listOf("first", "second").forEach(store::clear)
    }

    @Test
    fun hasNoPositionForNewTrack() {
        assertNull(store.position("first"))
    }

    @Test
    fun positionSurvivesNewStoreInstance() {
        store.save("first", 12_345L)

        assertEquals(12_345L, DiscoursePlaybackStore(context).position("first"))
    }

    @Test
    fun positionsRemainIndependent() {
        store.save("first", 100L)
        store.save("second", 200L)

        assertEquals(100L, store.position("first"))
        assertEquals(200L, store.position("second"))
    }

    @Test
    fun clearingPositionOnlyRemovesSelectedTrack() {
        store.save("first", 100L)
        store.save("second", 200L)

        store.clear("first")

        assertNull(store.position("first"))
        assertEquals(200L, store.position("second"))
    }

    @Test
    fun markingPlayedIsIndependentAndPersists() {
        store.markPlayed("first")

        val restored = DiscoursePlaybackStore(context)
        assertEquals(true, restored.isPlayed("first"))
        assertEquals(false, restored.isPlayed("second"))
    }

    @Test
    fun clearingPlayedOnlyRemovesSelectedTrack() {
        store.markPlayed("first")
        store.markPlayed("second")

        store.clearPlayed("first")

        assertEquals(false, store.isPlayed("first"))
        assertEquals(true, store.isPlayed("second"))
    }

    @Test
    fun mediaWrapperStartsAtStoredPosition() {
        store.save("first", 1_234L)

        assertEquals(1_234L, audio("first").toMediaWrapper(context).time)
    }

    private fun audio(id: String) = DiscourseAudio(
        id = id,
        discourseId = "discourse-$id",
        discourseName = "Discourse $id",
        discourseThumbnailUrl = null,
        language = "hindi",
        title = "Track $id",
        audioUrl = "https://example.test/$id.mp3",
        durationSeconds = 120.0,
        fileSize = 100L,
        mimeType = "audio/mpeg",
        trackNumber = 1,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z"
    )
}
