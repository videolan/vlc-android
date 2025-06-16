/*
 * ************************************************************************
 *  DisplaySettingsViewModel.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.KEY_CURRENT_EQUALIZER_ID
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.repository.EqualizerRepository

/**
 * View model storing data for the equalizer dialog
 *
 */
class EqualizerViewModel(context: Context, private val equalizerRepository: EqualizerRepository) : ViewModel() {
    val history = ArrayList<EqualizerWithBands>()
    var bandCount = -1
    var lastSaveToHistoryFrom = -2
    val settings = Settings.getInstance(context)
    var needForceRefresh = false

    val equalizerEntries = equalizerRepository.equalizerEntries.asLiveData()
    var currentEqualizerId = 1L
        set(value) {
            field = value
            settings.edit { putLong(KEY_CURRENT_EQUALIZER_ID, value) }
        }

    fun updateEqualizer() {
        if (!settings.getBoolean(KEY_EQUALIZER_ENABLED, false))
            PlaybackService.equalizer.value = MediaPlayer.Equalizer.create()
        else
            PlaybackService.equalizer.value =getCurrentEqualizer().getEqualizer()
    }

    init {
        currentEqualizerId = settings.getLong(KEY_CURRENT_EQUALIZER_ID, 1L)
    }

    /**
     * Save the current equalizer in history
     *
     */
    fun saveInHistory(from: Int) {
        if (BuildConfig.DEBUG) Log.d(EqualizerFragmentDialog.TAG, "saveInHistory: from $from, saving: ${from != lastSaveToHistoryFrom}, history size: ${history.size}")
        if (from != lastSaveToHistoryFrom)
            history.add(getCurrentEqualizer().copy())
        lastSaveToHistoryFrom = from
    }

    /**
     * Get last equalizer from history
     *
     * @return the last equalizer from history
     */
    fun undoFromHistory(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(EqualizerFragmentDialog.TAG, "undoFromHistory: history size: ${history.size}")
        lastSaveToHistoryFrom = -2
        if (history.isEmpty()) return@launch
        needForceRefresh = true
        equalizerRepository.addOrUpdateEqualizerWithBands(context, history.removeAt(history.lastIndex))
    }

    fun getCurrentEqualizer(): EqualizerWithBands {
        return equalizerEntries.value!!.first { it.equalizerEntry.id == currentEqualizerId }
    }

    fun updateCurrentPreamp(context: Context, f: Float) = viewModelScope.launch(Dispatchers.IO) {
        val currentEqualizer = getCurrentEqualizer()
        equalizerRepository.addOrUpdateEqualizerWithBands(
            context,
            currentEqualizer.copy(equalizerEntry = currentEqualizer.equalizerEntry.copy(preamp = f).apply { id = currentEqualizer.equalizerEntry.id })
        )
    }

    fun updateEqualizerBands(context: Context, bands: List<EqualizerBand>) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, getCurrentEqualizer().copy(bands = bands.sortedBy { it.index }))
    }

    fun createCustomEqualizer(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        val currentEqualizer = getCurrentEqualizer()
        val newEq = currentEqualizer.copy(equalizerEntry = currentEqualizer.equalizerEntry.copy(presetIndex = -1, name = currentEqualizer.equalizerEntry.name + " (copy)").apply { id = 0 })
        currentEqualizerId = equalizerRepository.addOrUpdateEqualizerWithBands(context, newEq)
    }
}

class EqualizerViewModelFactory(private val context: Context, private val repository: EqualizerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EqualizerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EqualizerViewModel(context.applicationContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
