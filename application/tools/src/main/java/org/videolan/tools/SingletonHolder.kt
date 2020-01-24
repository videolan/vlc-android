/*******************************************************************************
 *  SingletonHolder.kt
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

package org.videolan.tools

open class SingletonHolder<T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile var instance: T? = null

    fun getInstance(arg: A) = instance ?: synchronized(this) {
        val i2 = instance
        if (i2 != null) i2
        else {
            val created = creator!!(arg)
            instance = created
            creator = null
            created
        }
    }
}
