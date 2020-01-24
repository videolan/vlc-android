package org.videolan.vlc.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.core.Is.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.util.TestUtil

@RunWith(AndroidJUnit4::class)
class CustomDirectoryDaoTest: DbTest() {
    @Test
    fun insertTwoCustomDirectory_GetAllShouldReturnTwo() {
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        fakeCustomDirectories.forEach {
            db.customDirectoryDao().insert(it)
        }

        /*===========================================================*/
        val customDirectories = db.customDirectoryDao().getAll()
        assertThat(customDirectories.size, `is`(2))
        assertThat(customDirectories, hasItem(fakeCustomDirectories[0]))
        assertThat(customDirectories, hasItem(fakeCustomDirectories[1]))
    }


    @Test
    fun insertTwoSameCustomDirectory_GetAllShouldReturnOne() {
        val fakeCustomDirectories = TestUtil.createCustomDirectories(1)
        db.customDirectoryDao().insert(fakeCustomDirectories[0])
        db.customDirectoryDao().insert(fakeCustomDirectories[0])

        /*===========================================================*/
        val customDirectories = db.customDirectoryDao().getAll()
        assertThat(customDirectories.size, `is`(1))
        assertThat(customDirectories, hasItem(fakeCustomDirectories[0]))
    }

    @Test
    fun insertTwoCustomDirectory_GetShouldReturnEachOne() {
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        fakeCustomDirectories.forEach {
            db.customDirectoryDao().insert(it)
        }

        /*===========================================================*/
        val firstCustomDirectory = db.customDirectoryDao().get(fakeCustomDirectories[0].path)[0]
        val secondCustomDirectory = db.customDirectoryDao().get(fakeCustomDirectories[1].path)[0]
        assertThat(firstCustomDirectory, `is`(fakeCustomDirectories[0]))
        assertThat(secondCustomDirectory, `is`(fakeCustomDirectories[1]))
    }


    @Test
    fun insertNoneCustomDirectory_GetShouldReturnNull() {
        val customDirectory = db.customDirectoryDao().get("foo")
        assertThat(customDirectory.size, `is`(0))
    }


    @Test
    fun insertTwoCustomDirectory_DeleteOneShouldDeleteThatOne() {
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        fakeCustomDirectories.forEach {
            db.customDirectoryDao().insert(it)
        }

        /*===========================================================*/
        db.customDirectoryDao().delete(fakeCustomDirectories[0])
        val customDirectories = db.customDirectoryDao().getAll()
        assertThat(customDirectories.size, `is`(1))
        assertThat(customDirectories, hasItem(fakeCustomDirectories[1]))
    }
}
