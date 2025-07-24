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
import android.net.Uri
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXPORT_EQUALIZERS_FILE
import org.videolan.tools.KEY_CURRENT_EQUALIZER_ID
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.mediadb.models.EqualizerEntry
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.util.EqualizerUtil
import org.videolan.vlc.util.JsonUtil
import org.videolan.vlc.util.share
import java.io.File

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
    var presetToDelete:EqualizerWithBands? = null
    private var oldEqualizer: EqualizerWithBands? = null

    val equalizerUnfilteredEntries = equalizerRepository.equalizerEntriesUnfiltered.asLiveData()
    val equalizerEntries = MediatorLiveData<List<EqualizerWithBands>>().apply {
        addSource(equalizerUnfilteredEntries) {
            value = it.filter { !it.equalizerEntry.isDisabled }.sortedBy { it.equalizerEntry.presetIndex }
        }
    }

    var currentEqualizerId = 1L
        set(value) {
            field = value
            settings.edit { putLong(KEY_CURRENT_EQUALIZER_ID, value) }
            currentEqualizerIdLive.postValue(value)
        }

    fun updateEqualizer() {
        if (!settings.getBoolean(KEY_EQUALIZER_ENABLED, false))
            PlaybackService.equalizer.value = MediaPlayer.Equalizer.create()
        else
            PlaybackService.equalizer.value = getCurrentEqualizer().getEqualizer()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            currentEqualizerId = equalizerRepository.getCurrentEqualizer(context).equalizerEntry.id
        }
    }

    fun insert(context: Context, equalizerWithBands: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        val eq = EqualizerWithBands(equalizerWithBands.equalizerEntry.copy().apply { id = 0 }, equalizerWithBands.bands)
        equalizerRepository.addOrUpdateEqualizerWithBands(context, eq)
    }

    fun enable(context: Context, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(isDisabled = false).apply { id = equalizer.equalizerEntry.id }))
    }

    fun disable(context: Context, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(isDisabled = true).apply { id = equalizer.equalizerEntry.id }))
    }

    /**
     * Save the current equalizer in history
     *
     */
    fun saveInHistory(from: Int) {
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
        lastSaveToHistoryFrom = -2
        if (history.isEmpty()) return@launch
        needForceRefresh = true
        equalizerRepository.addOrUpdateEqualizerWithBands(context, history.removeAt(history.lastIndex))
    }

    fun clearHistory() {
        history.clear()
        lastSaveToHistoryFrom = -2
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

    fun createCustomEqualizer(context: Context, fromScratch: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val currentEqualizer = if (fromScratch)
                EqualizerWithBands(EqualizerEntry("", 0F, -1, false), buildList { for (i in 0 until bandCount) add(EqualizerBand(i, 0F)) })
            else
                getCurrentEqualizer()

        var newNameTemplate = if (fromScratch)
            context.getString(R.string.new_equalizer_copy_template, "")
        else
            currentEqualizer.equalizerEntry.name + " " + context.getString(R.string.equalizer_copy_template, "")

        var i = 0
        while (!isNameAllowed(newNameTemplate)) {
            ++i
            newNameTemplate = if (fromScratch)
                context.getString(R.string.new_equalizer_copy_template, " $i")
            else
                currentEqualizer.equalizerEntry.name + " " + context.getString(R.string.equalizer_copy_template, " $i")
        }
        val newEq = currentEqualizer.copy(equalizerEntry = currentEqualizer.equalizerEntry.copy(presetIndex = -1, name = newNameTemplate).apply { id = 0 })
        currentEqualizerId = equalizerRepository.addOrUpdateEqualizerWithBands(context, newEq)
    }

    fun deleteEqualizer(context: Context) {
        presetToDelete?.let {
            viewModelScope.launch(Dispatchers.IO) {
                if (it.equalizerEntry.presetIndex != -1) {
                    currentEqualizerId = equalizerEntries.value!!.first { it.equalizerEntry.id != currentEqualizerId }.equalizerEntry.id
                    val newEq = it.copy(equalizerEntry = it.equalizerEntry.copy(isDisabled = true).apply { id = it.equalizerEntry.id })
                    equalizerRepository.addOrUpdateEqualizerWithBands(context, newEq)
                } else {
                    currentEqualizerId = equalizerEntries.value!!.first { it.equalizerEntry.id != currentEqualizerId }.equalizerEntry.id
                    equalizerRepository.delete(it.equalizerEntry)
                }
            }
            presetToDelete = null
        }
    }

    /**
     * Delete an equalizer from the database
     *
     * @param equalizer the equalizer to delete
     */
    fun delete(equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        oldEqualizer = equalizer
        equalizerRepository.delete(equalizer.equalizerEntry)
    }

    /**
     * Restore the old equalizer
     *
     * @param context the context
     */
    fun restore(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, oldEqualizer!!)
    }

    /**
     * Is name allowed
     *
     * @param name the name to check
     * @return true if name is not empty and not already used
     */
    fun isNameAllowed(name: String): Boolean {
        return name.isNotBlank() && !equalizerUnfilteredEntries.value!!.any { it.equalizerEntry.name == name }
    }

    /**
     * Check if the name is forbidden because a default preset is using it
     *
     * @param name the name to check
     * @return true if name is forbidden
     */
    fun checkForbidden(name:String): Boolean {
        return equalizerUnfilteredEntries.value?.any { it.equalizerEntry.name == name && it.equalizerEntry.presetIndex != -1 } != false
    }

    /**
     * Update equalizer name
     *
     * @param context the context
     * @param name the new name
     */
    fun updateEqualizerName(context: Context, name: String) = viewModelScope.launch(Dispatchers.IO) {
        val currentEqualizer = getCurrentEqualizer()
        val newEq = currentEqualizer.copy(equalizerEntry = currentEqualizer.equalizerEntry.copy(name = name).apply { id = currentEqualizer.equalizerEntry.id })
        equalizerRepository.addOrUpdateEqualizerWithBands(context, newEq)
    }

    /**
     * Enable all the default equalizers
     *
     * @param context the context
     */
    fun showAll(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerUnfilteredEntries.value?.forEach {
            if (it.equalizerEntry.presetIndex != -1 && it.equalizerEntry.isDisabled)
                equalizerRepository.addOrUpdateEqualizerWithBands(context, it.copy(equalizerEntry = it.equalizerEntry.copy(isDisabled = false).apply { id = it.equalizerEntry.id }))
        }
    }

    /**
     * Disable all the default equalizers
     *
     * @param context the context
     */
    fun hideAll(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerUnfilteredEntries.value?.forEach {
            if (it.equalizerEntry.presetIndex != -1 && !it.equalizerEntry.isDisabled && it.equalizerEntry.id != currentEqualizerId)
                equalizerRepository.addOrUpdateEqualizerWithBands(context, it.copy(equalizerEntry = it.equalizerEntry.copy(isDisabled = true).apply { id = it.equalizerEntry.id }))
        }
    }

    /**
     * Export an equalizer to a file
     *
     * @param context the context
     * @param equalizer the equalizer to export
     */
    fun export(context: FragmentActivity, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        val characterFilter = Regex("[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]")
        val fileName: String? = equalizer.equalizerEntry.name
            .replace(characterFilter, "")
            .lowercase()
            .trim()
            .replace(" ", "_")
            .replace("/", "")
            .replace("\"", "")
        val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + File.separator + "vlc_" + fileName + ".json")
        UiTools.snackerConfirm(context, context.getString(R.string.equalizer_exported, dst.toString()), confirmMessage = R.string.share, overAudioPlayer = false) {
            context.share(dst)
        }
        if (context.getWritePermission(Uri.fromFile(dst))) {
            JsonUtil.convertToJson(equalizer).let {
                dst.writeText(it)
            }
        }
    }

    fun exportAll(context: FragmentActivity) = viewModelScope.launch(Dispatchers.IO) {
        EqualizerUtil.exportAllEqualizers(context)
        UiTools.snacker(context, context.getString(R.string.equalizer_exported, EXPORT_EQUALIZERS_FILE))
    }

    companion object {
        val currentEqualizerIdLive = MutableLiveData<Long>().apply { value = -1L }

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
