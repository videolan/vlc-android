package org.videolan.vlc.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.stubs.StubDataSource
import org.videolan.vlc.BaseTest
import org.videolan.vlc.util.ModelsHelper.getHeader

class ModelsHelperTest : BaseTest() {
    val dataSource: StubDataSource = StubDataSource.getInstance()

    override fun beforeTest() {
        super.beforeTest()
        dataSource.resetData()
    }

    @Test
    fun withDefaultSortingAndPreviousItemIsNullAndCurrentItemHasNoTitle_headerShouldBeSpecial() {
        val item = dataSource.createFolder("")
        assertEquals("#", getHeader(context, Medialibrary.SORT_DEFAULT, item, null))
    }

    @Test
    fun withDefaultSortingAndPreviousItemAndCurrentItemHaveSameInitials_headerShouldBeNull() {
        val item = dataSource.createFolder("abc")
        val aboveItem = dataSource.createFolder("Adef")
        assertEquals(null, getHeader(context, Medialibrary.SORT_DEFAULT, item, aboveItem))
    }

    @Test
    fun withDefaultSortingAndPreviousItemIsNullAndCurrentItemStartsWithNumber_headerShouldBeSpecial() {
        val item = dataSource.createFolder("9ab")
        assertEquals("#", getHeader(context, Medialibrary.SORT_DEFAULT, item, null))
    }

    @Test
    fun withDefaultSortingAndPreviousItemAndCurrentItemHaveDifferentInitials_headerShouldBeTheCurrentInitials() {
        val item = dataSource.createFolder("abc")
        val aboveItem = dataSource.createFolder("def")
        assertEquals("A", getHeader(context, Medialibrary.SORT_DEFAULT, item, aboveItem))
    }

    @Test
    fun withDefaultSortingAndPreviousItemAndCurrentItemStartWithDifferentNumber_headerShouldBeNull() {
        val item = dataSource.createFolder("91c")
        val aboveItem = dataSource.createFolder("7sc")
        assertEquals(null, getHeader(context, Medialibrary.SORT_DEFAULT, item, aboveItem))
    }

    @Test
    fun withDurationSortingAndPreviousItemIsNullAndCurrentItemIsZeroDuration_headerShouldBePlaceholder() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2018, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals("-", getHeader(context, Medialibrary.SORT_DURATION, item, null))
    }

    @Test
    fun withDurationSortingAndPreviousItemIsNullAndCurrentItemIsFolder_headerShouldBePlaceholder() {
        val item = dataSource.createFolder("abc")
        assertEquals("-", getHeader(context, Medialibrary.SORT_DURATION, item, null))
    }

    @Test
    fun withDurationSortingAndPreviousItemIsNullAndCurrentItemIsAlbum_headerShouldBeLengthToCategory() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2018, "Artwork", "Dummy", 9, 1, 1, 48000)
        assertEquals(item.duration.lengthToCategory(), getHeader(context, Medialibrary.SORT_DURATION, item, null))
    }

    @Test
    fun withDurationSortingAndPreviousItemAndCurrentItemAreBothFolder_headerShouldBeNull() {
        val item = dataSource.createFolder("abc")
        val aboveItem = dataSource.createFolder("cyz")
        assertEquals(null, getHeader(context, Medialibrary.SORT_DURATION, item, aboveItem))
    }

    @Test
    fun withDurationSortingAndPreviousItemAndCurrentItemAreBothAlbumLessThanMinute_headerShouldBeNull() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2018, "Artwork", "Dummy", 9, 1, 1, 48000)
        val aboveItem = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2018, "Artwork", "Dummy", 9, 1, 51000)
        assertEquals(null, getHeader(context, Medialibrary.SORT_DURATION, item, aboveItem))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemIsNullAndCurrentItemIsFolder_headerShouldBeSpecial() {
        val item = dataSource.createFolder("test")
        assertEquals("-", getHeader(context, Medialibrary.SORT_RELEASEDATE, item, null))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemIsNullAndCurrentItemIsAlbumWithNoDate_headerShouldBeSpecial() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 0, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals("-", getHeader(context, Medialibrary.SORT_RELEASEDATE, item, null))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemIsNullAndCurrentItemIsAlbumWith2019Date_headerShouldBe2019() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2019, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals("2019", getHeader(context, Medialibrary.SORT_RELEASEDATE, item, null))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemAndCurrentItemAreAlbumWithNoDate_headerShouldBeNull() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 0, "Artwork", "Dummy", 9, 1, 1, 0)
        val aboveItem = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "dEF", 0, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals(null, getHeader(context, Medialibrary.SORT_RELEASEDATE, item, aboveItem))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemAndCurrentItemAreAlbumWithSameDate_headerShouldBeNull() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2019, "Artwork", "Dummy", 9, 1, 1, 0)
        val aboveItem = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "dEF", 2019, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals(null, getHeader(context, Medialibrary.SORT_RELEASEDATE, item, aboveItem))
    }

    @Test
    fun withReleaseDateSortingAndPreviousItemIsAlbumWith2020DateAndCurrentItemIsAlbumWith2019Date_headerShouldBe2019() {
        val item = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "Abc", 2019, "Artwork", "Dummy", 9, 1, 1, 0)
        val aboveItem = MLServiceLocator.getAbstractAlbum(dataSource.uuid, "dEF", 2020, "Artwork", "Dummy", 9, 1, 1, 0)
        assertEquals("2019", getHeader(context, Medialibrary.SORT_RELEASEDATE, item, aboveItem))
    }
}