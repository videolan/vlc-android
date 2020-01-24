/*******************************************************************************
 *  DbTest.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 ******************************************************************************/

package org.videolan.vlc.database

import androidx.arch.core.executor.testing.CountingTaskExecutorRule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.TimeUnit


abstract class DbTest {
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val countingTaskExecutorRule = CountingTaskExecutorRule()
    private lateinit var _db: MediaDatabase
    val db: MediaDatabase
        get() = _db

    @Before
    fun initDb() {
        _db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getTargetContext(),
            MediaDatabase::class.java)
            .build()
    }

    @After
    fun closeDb() {
        countingTaskExecutorRule.drainTasks(10, TimeUnit.SECONDS)
        _db.close()
    }

}
