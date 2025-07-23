/*
 * ************************************************************************
 *  EqualizerUtil.kt
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

package org.videolan.vlc.util

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXPORT_EQUALIZERS_FILE
import org.videolan.tools.CloseableUtils
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.repository.EqualizerRepository
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

object EqualizerUtil {
    /**
     * Export all the custom equalizers to a dedicated file
     *
     * @param context the context to be used
     */
    suspend fun exportAllEqualizers(context: Context) = withContext(Dispatchers.IO) {
        val eqs = getEqualizerExportString(context)
        val stream: FileOutputStream
        val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + EXPORT_EQUALIZERS_FILE)
        try {
            stream = FileOutputStream(dst)
            val output = OutputStreamWriter(stream)
            val bw = BufferedWriter(output)
            try {
                bw.write(eqs)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                CloseableUtils.close(bw)
                CloseableUtils.close(output)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }

    fun getEqualizerExport(context: Context): EqualizerExport {
        val customEqualizers = EqualizerRepository.getInstance(context).getCustomEqualizers()
        val defaultEqualizers = EqualizerRepository.getInstance(context).getDefaultEqualizers()
        return EqualizerExport(
            customEqualizers,
            EqualizerRepository.getInstance(context).getCurrentEqualizer(context).equalizerEntry.name,
            defaultEqualizers.map { EqualizerState(it.equalizerEntry.name, it.equalizerEntry.isDisabled) }
        )
    }

    fun getEqualizerExportString(context: Context) = JsonUtil.convertToJson(getEqualizerExport(context))

    fun escapeString(string: String): String? {
        val moshi = Moshi
            .Builder()
            .build()
        return moshi.adapter(String::class.java).toJson(string)
    }


    /**
     * Import all the custom equalizers and states
     *
     * @param context the context to be used
     * @param equalizerExport the equalizer export to be imported
     * @param listener the listener to be called when the import is finished
     */
    suspend fun importAll(context: Context, equalizerExport:EqualizerExport, listener: ((id: Long) -> Unit)?) = withContext(Dispatchers.IO) {
        val equalizerRepository = EqualizerRepository.getInstance(context)
        equalizerExport.equalizers.forEach { equalizer ->
            equalizer.equalizerEntry.id = 0
            equalizer.bands.forEach {
                it.equalizerEntry = 0
            }
            equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer)
        }
        equalizerExport.defaultStates.forEach {
            val equalizer = equalizerRepository.getByName(it.name)
            equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(isDisabled = it.disabled).apply { id = equalizer.equalizerEntry.id }))
        }
        val currentEq = equalizerRepository.getByName(equalizerExport.currentEqualizerName)
        withContext(Dispatchers.Main) { listener?.invoke(currentEq.equalizerEntry.id) }
    }


    /**
     * Import all the custom equalizers
     *
     * @param context the context to be used
     * @param json the json string to be imported
     */
    suspend fun importAll(context: Context, json:String, listener: (id: Long) -> Unit) = withContext(Dispatchers.IO) {
        importAll(context,  JsonUtil.getEqualizersFromJson(json), listener)
    }
}

data class EqualizerExport(
    val equalizers: List<EqualizerWithBands>,
    val currentEqualizerName: String,
    val defaultStates: List<EqualizerState>,
    private val jsonType:String = "equalizer_export"
)

data class EqualizerState(
    val name: String,
    val disabled: Boolean
)