/*
 * ************************************************************************
 *  MedialibraryDelegate.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.ACTION_DISCOVER_DEVICE
import org.videolan.resources.EXTRA_PATH
import org.videolan.resources.util.launchForeground
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.ScanProgress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegate class responsible for managing Media Library background tasks and storage detection.
 * It tracks scanning progress and handles notifications for new external storage devices.
 *
 * @property application The application context used for launching foreground services.
 */
@Singleton
class MedialibraryDelegate @Inject constructor(private val application: Application) {

    private val _newStorageDetected = MutableStateFlow<String?>(null)
    val newStorageDetected = _newStorageDetected.asStateFlow()

    val progress = MutableLiveData<ScanProgress?>(null)

    private var progressJob: Job? = null
    private var workingJob: Job? = null
    private var storageJob: Job? = null

    /**
     * Initializes the background jobs for tracking progress and storage changes.
     * This should typically be called from the ViewModel's init block.
     *
     * @param scope The [CoroutineScope] in which to launch the background jobs.
     */
    fun setup(scope: CoroutineScope) {
        progressJob = scope.launch {
            MediaParsingService.progress.asFlow().collect {
                if (Medialibrary.getState().value == false) {
                    progress.value = null
                    return@collect
                }
                progress.value = it
            }
        }

        workingJob = scope.launch {
            Medialibrary.getState().asFlow().collect {
                if (!it) progress.value = null
            }
        }

        storageJob = scope.launch {
            MediaParsingService.newStorages.asFlow().collect { devices ->
                if (devices.isNullOrEmpty()) return@collect
                _newStorageDetected.value = devices.first()
                MediaParsingService.newStorages.value = null
            }
        }
    }

    /**
     * Cancels all active background jobs.
     * Should be called when the associated lifecycle (e.g., ViewModel) is cleared.
     */
    fun onCleared() {
        progressJob?.cancel()
        workingJob?.cancel()
        storageJob?.cancel()
    }

    /**
     * Accepts a new storage device and starts the discovery process.
     *
     * @param path The path to the new storage device.
     */
    fun acceptStorage(path: String) {
        val intent = Intent(ACTION_DISCOVER_DEVICE, null, application, MediaParsingService::class.java)
                .putExtra(EXTRA_PATH, path)
        application.launchForeground(intent)
        _newStorageDetected.value = null
    }

    /**
     * Declines a new storage device, dismissing the detection notification.
     */
    fun declineStorage() {
        _newStorageDetected.value = null
    }
}
