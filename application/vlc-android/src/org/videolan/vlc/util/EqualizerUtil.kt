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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXPORT_EQUALIZERS_FILE
import org.videolan.tools.CloseableUtils
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
        val equalizers = EqualizerRepository.getInstance(context).getCustomEqualizers()
        if (equalizers.isNotEmpty()) {
            val eqs = JsonUtil.convertToJson(equalizers)
            var success = false
            val stream: FileOutputStream
            val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + EXPORT_EQUALIZERS_FILE)
            try {
                stream = FileOutputStream(dst)
                val output = OutputStreamWriter(stream)
                val bw = BufferedWriter(output)
                try {
                    bw.write(eqs)
                    success = true
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
    }

    /**
     * Import all the custom equalizers
     *
     * @param context the context to be used
     * @param json the json string to be imported
     */
    suspend fun importAll(context: Context, json:String) = withContext(Dispatchers.IO) {
        val equalizers = JsonUtil.getEqualizersFromJson(json)
        equalizers?.forEach { equalizer ->
            equalizer.equalizerEntry.id = 0
            equalizer.bands.forEach {
                it.equalizerEntry = 0
            }
            EqualizerRepository.getInstance(context).addOrUpdateEqualizerWithBands(context, equalizer)
        }
    }
}