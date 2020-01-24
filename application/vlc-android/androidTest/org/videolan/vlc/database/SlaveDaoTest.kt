/*******************************************************************************
 *  SlaveDaoTest.kt
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.util.TestUtil

@RunWith(AndroidJUnit4::class)
class SlaveDaoTest: DbTest() {

    @Test fun insertTwoSubtitleSlave_GetShouldReturnJustLatOne() {
        val fakeSlaves = TestUtil.createSubtitleSlavesForMedia("foo", 2)
        fakeSlaves.forEach {
            db.slaveDao().insert(it)
        }

        /*===========================================================*/

        val slaves = db.slaveDao().get(fakeSlaves[0].mediaPath)
        assertThat(slaves.size, equalTo(1))
        assertThat(slaves, hasItem(fakeSlaves[1]))
    }
}
