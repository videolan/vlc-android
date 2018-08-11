package org.videolan.vlc.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StringsTest {

    @Test
    fun stripTrailingSlash() {
        var s = Strings.stripTrailingSlash("foo/")
        assertEquals(s, "foo")

        s = Strings.stripTrailingSlash("bar")
        assertEquals(s, "bar")
    }

    @Test
    fun isStringStartsWithOneOfArrayItems_returnTrue() {
        val array = arrayOf("java", "kotlin", "python")
        var bool = Strings.startsWith(array, "${array[0]}Foo")
        assertTrue(bool)

        bool = Strings.startsWith(array, "${array[1]}Foo")
        assertTrue(bool)

        bool = Strings.startsWith(array, "${array[2]}Foo")
        assertTrue(bool)

        bool = Strings.startsWith(array, "${array[2]}")
        assertTrue(bool)
    }

    @Test
    fun isStringStartsWithOneOfArrayItems_returnFalse() {
        val array = arrayOf("java", "kotlin", "python")
        var bool = Strings.startsWith(array, "Foo${array[0]}")
        assertFalse(bool)

        bool = Strings.startsWith(array, "foo${array[1]}")
        assertFalse(bool)

        bool = Strings.startsWith(array, "foo${array[2]}")
        assertFalse(bool)

        bool = Strings.startsWith(array, "")
        assertFalse(bool)

        bool = Strings.startsWith(array, "Ruby")
        assertFalse(bool)
    }


    @Test
    fun indexOfListItemEndsWithString_returnIndex() {
        val list = listOf("fooJava", "fooKotlin", "fooPython", "barPython")
        var indx = Strings.containsName(list, "Java")
        assertEquals(indx, 0)

        indx = Strings.containsName(list, "Kotlin")
        assertEquals(indx, 1)

        indx = Strings.containsName(list, "Python")
        assertEquals(indx, 3)

        indx = Strings.containsName(list, "fooPython")
        assertEquals(indx, 2)
    }


    @Test
    fun indexOfListItemEndsWithString_returnMinusOne() {
        val list = listOf("fooJava", "fooKotlin", "fooPython")
        var indx = Strings.containsName(list, "Jav")
        assertEquals(indx, -1)

        indx = Strings.containsName(list, "javaBar")
        assertEquals(indx, -1)

        indx = Strings.containsName(list, "KotlinBar")
        assertEquals(indx, -1)

        indx = Strings.containsName(list, "PythonBar")
        assertEquals(indx, -1)

        indx = Strings.containsName(list, "fooPythonBar")
        assertEquals(indx, -1)
    }

    @Test
    fun removeFileProtocole() {
        var s = Strings.removeFileProtocole("file://foopath")
        assertEquals(s, "foopath")

        s = Strings.removeFileProtocole("barpath")
        assertEquals(s, "barpath")
    }

    @Test
    fun readableFileSize() {
        var s = Strings.readableFileSize(10)
        assertEquals(s, "10 B")

        s = Strings.readableFileSize(1026)
        assertEquals(s, "1 KiB")

        s = Strings.readableFileSize(10026)
        assertEquals(s, "9.8 KiB")

        s = Strings.readableFileSize(100026)
        assertEquals(s, "97.7 KiB")

        s = Strings.readableFileSize(1000026)
        assertEquals(s, "976.6 KiB")

        s = Strings.readableFileSize(10000026)
        assertEquals(s, "9.5 MiB")


        s = Strings.readableFileSize(1000000026)
        assertEquals(s, "953.7 MiB")

        s = Strings.readableFileSize(10000000026)
        assertEquals(s, "9.3 GiB")

        s = Strings.readableFileSize(100000000026)
        assertEquals(s, "93.1 GiB")
    }

    @Test
    fun readableSize() {
        var s = Strings.readableSize(10)
        assertEquals(s, "10 B")

        s = Strings.readableSize(1026)
        assertEquals(s, "1 KB")

        s = Strings.readableSize(10026)
        assertEquals(s, "10 KB")

        s = Strings.readableSize(100026)
        assertEquals(s, "100 KB")

        s = Strings.readableSize(1000026)
        assertEquals(s, "1 MB")

        s = Strings.readableSize(10000026)
        assertEquals(s, "10 MB")


        s = Strings.readableSize(1000000026)
        assertEquals(s, "1 GB")

        s = Strings.readableSize(10000000026)
        assertEquals(s, "10 GB")

        s = Strings.readableSize(100000000026)
        assertEquals(s, "100 GB")
    }


}