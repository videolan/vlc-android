package org.videolan.vlc.discourse

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.videolan.tools.Settings
import org.videolan.vlc.BaseTest

class DiscourseRepositoryTest : BaseTest() {
    private lateinit var repository: DiscourseRepository

    @Before
    fun clearRecentlyPlayed() {
        Settings.getInstance(context).edit()
            .remove("osho_api_recently_played_discourses")
            .remove("osho_api_recently_played_audios")
            .commit()
        repository = DiscourseRepository(context, mockk())
    }

    @Test
    fun storesFullDiscoursesNewestFirstAndCapsAt24() {
        val discourses = (1..25).map { discourse("discourse-$it") }

        discourses.forEach(repository::recordRecentlyPlayed)

        assertEquals(24, repository.recentlyPlayedDiscourses.size)
        assertEquals(discourses.last(), repository.recentlyPlayedDiscourses.first())
        assertEquals(discourses[1], repository.recentlyPlayedDiscourses.last())
    }

    @Test
    fun replayingDiscourseMovesItToTheFrontWithoutDuplication() {
        val first = discourse("first")
        val second = discourse("second")
        repository.recordRecentlyPlayed(first)
        repository.recordRecentlyPlayed(second)

        repository.recordRecentlyPlayed(first)

        assertEquals(listOf(first, second), repository.recentlyPlayedDiscourses)
    }

    @Test
    fun storesFullAudiosNewestFirstAndCapsAt24() {
        val audios = (1..25).map { audio("audio-$it") }

        audios.forEach(repository::recordRecentlyPlayed)

        assertEquals(24, repository.recentlyPlayedAudios.size)
        assertEquals(audios.last(), repository.recentlyPlayedAudios.first())
        assertEquals(audios[1], repository.recentlyPlayedAudios.last())
    }

    @Test
    fun replayingAudioMovesItToTheFrontWithoutDuplication() {
        val first = audio("first")
        val second = audio("second")
        repository.recordRecentlyPlayed(first)
        repository.recordRecentlyPlayed(second)

        repository.recordRecentlyPlayed(first)

        assertEquals(listOf(first, second), repository.recentlyPlayedAudios)
    }

    private fun discourse(id: String) = Discourse(
        id = id,
        title = "Title $id",
        thumbnailUrl = "https://example.test/$id.jpg",
        isAudioCleaned = true,
        language = "hindi",
        slug = "slug-$id",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z",
        totalTracks = 3,
        totalLikes = 7
    )

    private fun audio(id: String) = DiscourseAudio(
        id = id,
        discourseId = "discourse-$id",
        discourseName = "Discourse $id",
        discourseThumbnailUrl = "https://example.test/$id.jpg",
        language = "hindi",
        title = "Track $id",
        audioUrl = "https://example.test/$id.mp3",
        durationSeconds = 120.0,
        fileSize = 100L,
        mimeType = "audio/mpeg",
        trackNumber = 1,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z",
        totalLikes = 7
    )
}
