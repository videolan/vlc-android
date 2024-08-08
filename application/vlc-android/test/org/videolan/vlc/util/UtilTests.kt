package org.videolan.vlc.util

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Comparator

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
    @Test
    fun mergeLists() {
        val list1 = mutableListOf(1, 5, 8, 9)
        val list2 = listOf(3, 4, 7)
        list1.mergeSorted(list2, Comparator.naturalOrder())
        Assert.assertEquals(listOf(1, 3, 4, 5, 7, 8, 9), list1)
    }
}