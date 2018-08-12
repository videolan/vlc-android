package org.videolan.vlc.repository

import android.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.powermock.modules.junit4.PowerMockRunner
import org.videolan.vlc.database.CustomDirectoryDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.models.CustomDirectory
import org.videolan.vlc.util.TestUtil
import org.videolan.vlc.util.argumentCaptor
import org.videolan.vlc.util.mock
import org.videolan.vlc.util.uninitialized

@RunWith(PowerMockRunner::class)
class CustomDirectoryRepositoryTest {
    private val customDirectoryDao = mock<CustomDirectoryDao>()
    private lateinit var customDirectoryRepository: CustomDirectoryRepository

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before fun init() {
        System.setProperty("kotlinx.coroutines.blocking.checker", "disable")
        val db = mock<MediaDatabase>()
        `when`(db.customDirectoryDao()).thenReturn(customDirectoryDao)
        customDirectoryRepository = CustomDirectoryRepository(customDirectoryDao)
    }

    @Test
    fun insertTwoCustomDirectory_GetAllShouldReturnTwo() = runBlocking{
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        fakeCustomDirectories.forEach {
            customDirectoryRepository.addCustomDirectory(it.path).join()
        }

        val inserted = argumentCaptor<CustomDirectory>()
        verify(customDirectoryDao, times(2)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(2))
        assertThat(inserted.allValues[0], `is`(fakeCustomDirectories[0]))
        assertThat(inserted.allValues[1], `is`(fakeCustomDirectories[1]))


        `when`(customDirectoryDao.getAll()).thenReturn(fakeCustomDirectories)

        val customDirectories = customDirectoryRepository.getCustomDirectories()
        verify(customDirectoryDao).getAll()
        assertThat(customDirectories.size, `is`(2))
    }

    @Test
    fun insertTwoCustomDirectory_DeleteOneShouldDeleteOne() = runBlocking{
        val fakeCustomDirectories = TestUtil.createCustomDirectories(2)
        fakeCustomDirectories.forEach {
            customDirectoryRepository.addCustomDirectory(it.path).join()
        }

        val inserted = argumentCaptor<CustomDirectory>()
        verify(customDirectoryDao, times(2)).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(2))
        assertThat(inserted.allValues[0], `is`(fakeCustomDirectories[0]))
        assertThat(inserted.allValues[1], `is`(fakeCustomDirectories[1]))

        customDirectoryRepository.deleteCustomDirectory(fakeCustomDirectories[0].path)

        val deleted = argumentCaptor<CustomDirectory>()
        verify(customDirectoryDao).delete(deleted.capture() ?: uninitialized())
        assertThat(deleted.value, `is`(fakeCustomDirectories[0]))
    }

    @Test
    fun insertOneCustomDirectory_CheckExistenceShouldBeTrue() = runBlocking{
        val fakeCustomDirectories = TestUtil.createCustomDirectories(1)
        fakeCustomDirectories.forEach {
            customDirectoryRepository.addCustomDirectory(it.path).join()
        }

        val inserted = argumentCaptor<CustomDirectory>()
        verify(customDirectoryDao).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(1))
        assertThat(inserted.allValues[0], `is`(fakeCustomDirectories[0]))

        `when`(customDirectoryDao.get(fakeCustomDirectories[0].path)).thenReturn(fakeCustomDirectories)

        val bool = customDirectoryRepository.customDirectoryExists(fakeCustomDirectories[0].path)
        assertTrue(bool)
    }

    @Test
    fun insertOneCustomDirectory_CheckExistenceForWrongPathShouldBeFalse() = runBlocking{
        val fakeCustomDirectories = TestUtil.createCustomDirectories(1)
        fakeCustomDirectories.forEach {
            customDirectoryRepository.addCustomDirectory(it.path).join()
        }

        val inserted = argumentCaptor<CustomDirectory>()
        verify(customDirectoryDao).insert(inserted.capture() ?: uninitialized())
        assertThat(inserted.allValues.size, `is`(1))
        assertThat(inserted.allValues[0], `is`(fakeCustomDirectories[0]))

        `when`(customDirectoryDao.get(fakeCustomDirectories[0].path)).thenReturn(fakeCustomDirectories)

        val bool = customDirectoryRepository.customDirectoryExists(fakeCustomDirectories[0].path+"foo")
        assertFalse(bool)
    }

}
