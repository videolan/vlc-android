package org.videolan.vlc.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.videolan.tools.*

@RunWith(JUnit4::class)
class StringsTest {

    @Test
    fun stripTrailingSlash() {
        assertEquals("foo/".stripTrailingSlash(), "foo")

        assertEquals("bar".stripTrailingSlash(), "bar")
    }

    @Test
    fun isStringStartsWithOneOfArrayItems_returnTrue() {
        val array = arrayOf("java", "kotlin", "python")
        var bool = startsWith(array, "${array[0]}Foo")
        assertTrue(bool)

        bool = startsWith(array, "${array[1]}Foo")
        assertTrue(bool)

        bool = startsWith(array, "${array[2]}Foo")
        assertTrue(bool)

        bool = startsWith(array, "${array[2]}")
        assertTrue(bool)
    }

    @Test
    fun isStringStartsWithOneOfArrayItems_returnFalse() {
        val array = arrayOf("java", "kotlin", "python")
        var bool = startsWith(array, "Foo${array[0]}")
        assertFalse(bool)

        bool = startsWith(array, "foo${array[1]}")
        assertFalse(bool)

        bool = startsWith(array, "foo${array[2]}")
        assertFalse(bool)

        bool = startsWith(array, "")
        assertFalse(bool)

        bool = startsWith(array, "Ruby")
        assertFalse(bool)
    }


    @Test
    fun indexOfListItemEndsWithString_returnIndex() {
        val list = listOf("fooJava", "fooKotlin", "fooPython", "barPython")
        var indx = containsName(list, "Java")
        assertEquals(indx, 0)

        indx = containsName(list, "Kotlin")
        assertEquals(indx, 1)

        indx = containsName(list, "Python")
        assertEquals(indx, 3)

        indx = containsName(list, "fooPython")
        assertEquals(indx, 2)
    }


    @Test
    fun indexOfListItemEndsWithString_returnMinusOne() {
        val list = listOf("fooJava", "fooKotlin", "fooPython")
        var indx = containsName(list, "Jav")
        assertEquals(indx, -1)

        indx = containsName(list, "javaBar")
        assertEquals(indx, -1)

        indx = containsName(list, "KotlinBar")
        assertEquals(indx, -1)

        indx = containsName(list, "PythonBar")
        assertEquals(indx, -1)

        indx = containsName(list, "fooPythonBar")
        assertEquals(indx, -1)
    }

    @Test
    fun removeFileScheme() {
        var s = "file://foopath".removeFileScheme()
        assertEquals(s, "foopath")

        s = "barpath".removeFileScheme()
        assertEquals(s, "barpath")
    }

    @Test
    fun readableFileSize() {
        var s = 10L.readableFileSize()
        assertEquals(s, "10 B")

        s = 1026L.readableFileSize()
        assertEquals(s, "1 KiB")

        s = 10026L.readableFileSize()
        assertEquals(s, "9.8 KiB")

        s = 100026L.readableFileSize()
        assertEquals(s, "97.7 KiB")

        s = 1000026L.readableFileSize()
        assertEquals(s, "976.6 KiB")

        s = 10000026L.readableFileSize()
        assertEquals(s, "9.5 MiB")


        s = 1000000026L.readableFileSize()
        assertEquals(s, "953.7 MiB")

        s = 10000000026L.readableFileSize()
        assertEquals(s, "9.3 GiB")

        s = 100000000026L.readableFileSize()
        assertEquals(s, "93.1 GiB")
    }

    @Test
    fun readableSize() {
        var s = 10L.readableSize()
        assertEquals(s, "10 B")

        s = 1026L.readableSize()
        assertEquals(s, "1 KB")

        s = 10026L.readableSize()
        assertEquals(s, "10 KB")

        s = 100026L.readableSize()
        assertEquals(s, "100 KB")

        s = 1000026L.readableSize()
        assertEquals(s, "1 MB")

        s = 10000026L.readableSize()
        assertEquals(s, "10 MB")


        s = 1000000026L.readableSize()
        assertEquals(s, "1 GB")

        s = 10000000026L.readableSize()
        assertEquals(s, "10 GB")

        s = 100000000026L.readableSize()
        assertEquals(s, "100 GB")
    }


}