/*
 * ************************************************************************
 *  LiveDataset.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.tools.livedata

import androidx.lifecycle.MutableLiveData


class LiveDataset<T> : MutableLiveData<MutableList<T>>() {

    private val emptyList = mutableListOf<T>()

    private var internalList = emptyList

    fun isEmpty() = internalList.isEmpty()

    override fun setValue(value: MutableList<T>?) {
        internalList = value ?: emptyList
        super.setValue(value)
    }

    override fun getValue() = internalList

    fun get(position: Int) = internalList[position]

    fun getList() = internalList.toList()

    val size = internalList.size

    fun clear() {
        value = internalList.apply { clear() }
    }

    fun add(item: T) {
        value = internalList.apply { add(item) }
    }

    fun add(item: T, comparator: Comparator<T>) {
        var position = 0
        for (media in internalList) {
            if (comparator.compare(item, media) > 0) position++
            else break
        }
        value = internalList.apply { this.add(position, item) }
    }

    fun add(position: Int, item: T) {
        value = internalList.apply { add(position, item) }
    }

    fun add(items: List<T>) {
        value = internalList.apply { addAll(items.filter { !this.contains(it) }) }
    }

    fun remove(item: T) {
        value = internalList.apply { remove(item) }
    }

    fun remove(position: Int) {
        value = internalList.apply { removeAt(position) }
    }

    fun move(item: T, newIndex: Int) {
        move(internalList.indexOf(item), newIndex)
    }

    fun move(from: Int, to: Int) {
        value = internalList.apply {
            val item = this[from]
            removeAt(from)
            if (from > to)
                add(to, item)
            else
                add(to - 1, item)
        }
    }
}