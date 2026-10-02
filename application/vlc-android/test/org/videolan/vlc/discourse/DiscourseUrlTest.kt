package org.videolan.vlc.discourse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscourseUrlTest {
    @Test fun resolvesCdnUrlsAndRejectsUnsafeSchemes() {
        assertEquals("https://example.test/a.mp3", resolveDiscourseUrl("https://example.test/a.mp3"))
        assertEquals(
            "https://osho.b-cdn.net/OSHO/Adhyatam%20Upanishad/track%201.mp3",
            resolveDiscourseUrl("/Adhyatam Upanishad/track 1.mp3")
        )
        assertNull(resolveDiscourseUrl("http://example.test/a.mp3"))
        assertNull(resolveDiscourseUrl("file:///tmp/a.mp3"))
    }

    @Test fun validatesCompletedFilesAndDeduplicatesDownloads() {
        assertEquals(true, isVerifiedDownload(100L, 100L))
        assertEquals(true, isVerifiedDownload(100L, null))
        assertEquals(false, isVerifiedDownload(99L, 100L))
        assertEquals(false, isVerifiedDownload(null, 100L))
        assertEquals(false, shouldEnqueue(DiscourseDownloadState.DOWNLOADED))
        assertEquals(false, shouldEnqueue(DiscourseDownloadState.DOWNLOADING))
        assertEquals(true, shouldEnqueue(DiscourseDownloadState.FAILED))
    }
}
