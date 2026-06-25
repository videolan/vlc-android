/*
 * ************************************************************************
 *  MainActivityViewModel.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.media.MediaLibraryItem

class FileBrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val _currentPathEntry: MutableStateFlow<MediaLibraryItem?> = MutableStateFlow(null)
    val currentPathEntry: StateFlow<MediaLibraryItem?> = _currentPathEntry.asStateFlow()

    lateinit var prepareSegmentsListener : (MediaLibraryItem, ArrayList<String>) -> Unit
    private val backStack = ArrayList<MediaLibraryItem>()
    private val focusBackStack = HashMap<MediaLibraryItem, String>()
    val focusToRestore = MutableStateFlow("")


    fun setCurrentPathEntry(item: MediaLibraryItem, lastFocusedKey: String = "") {
        viewModelScope.launch {
            _currentPathEntry.value?.let {
                backStack.add(it)
                if (lastFocusedKey.isNotEmpty()) {
                    focusBackStack[it] = lastFocusedKey
                }
            }
            focusToRestore.value = ""
            _currentPathEntry.emit(item)
        }
    }

    fun popBackStack(): Boolean {
        var popped = false
        if (backStack.isNotEmpty()) {
            val entry = backStack.removeLastOrNull()
            entry?.let {
                val restoredKey = focusBackStack.remove(it) ?: ""
                focusToRestore.value = restoredKey
                popped = true
                viewModelScope.launch {
                    _currentPathEntry.emit(entry)
                }
            }
        }
        return popped
    }

    fun addPrepareSegmentsListener(listener: (MediaLibraryItem, ArrayList<String>) -> Unit) {
        prepareSegmentsListener = listener
    }

    fun prepareSegments():List<String> {
        val result = ArrayList<String>()
        _currentPathEntry.value?.let {
            prepareSegmentsListener(it, result)
        }
        return result
    }
}
