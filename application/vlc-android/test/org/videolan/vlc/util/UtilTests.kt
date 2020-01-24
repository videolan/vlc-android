package org.videolan.vlc.util

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExtensionsTests {

    @Test
    fun getResolutionClass() {
        Assert.assertEquals("720p", generateResolutionClass(1280, 536))
        Assert.assertEquals("720p", generateResolutionClass(1280, 720))
        Assert.assertEquals("SD", generateResolutionClass(848, 480))
        Assert.assertEquals("4K", generateResolutionClass(3840, 2160))
        Assert.assertEquals("SD", generateResolutionClass(640, 352))
        Assert.assertEquals("1080p", generateResolutionClass(1920, 1080))
        Assert.assertEquals("SD", generateResolutionClass(480, 272))
        Assert.assertEquals("SD", generateResolutionClass(720, 576))
        Assert.assertEquals("1080p", generateResolutionClass(2048, 1024))
        Assert.assertEquals("SD", generateResolutionClass(712, 480))
        Assert.assertEquals("SD", generateResolutionClass(716, 480))
    }
}