/**
 * **************************************************************************
 * FlagSet.kt
 * ****************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 *
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
 * ***************************************************************************
 */
package org.videolan.vlc.util

import java.util.EnumSet

class FlagSet<T>(private val enumClass: Class<T>) where T : Enum<T>, T : Flag {
    private val enabledActions = EnumSet.noneOf(enumClass)

    override fun toString() = enabledActions.toString()

    fun add(action: T) = enabledActions.add(action)

    fun remove(action: T) = enabledActions.remove(action)

    fun addAll(vararg actions: T) = actions.forEach { add(it) }

    fun removeAll(vararg actions: T) = actions.forEach { remove(it) }

    fun contains(action: T) = enabledActions.contains(action)

    fun isNotEmpty() = enabledActions.isNotEmpty()

    fun getCapabilities(): Long = enabledActions.fold(0L) { capabilities, action -> capabilities or action.toLong() }

    fun setCapabilities(capabilities: Long) {
        if (capabilities == 0L) return

        var remainingBits = capabilities
        for (action in enumClass.enumConstants) {
            val element = action.toLong()
            if (capabilities and element != 0L) {
                enabledActions.add(action)
                remainingBits = remainingBits xor element
                if (remainingBits == 0L) break
            }
        }
    }
}

interface Flag {
    fun toLong(): Long
}
