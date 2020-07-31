/*
 * ************************************************************************
 *  Strings.kt
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

/*****************************************************************************
 * Strings.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 */

@file:JvmName("Strings")

package org.videolan.tools

import java.text.DecimalFormat
import java.util.*

private const val TAG = "VLC/UiTools/Strings"

fun String.stripTrailingSlash() = if (endsWith("/") && length > 1) dropLast(1) else this

//TODO: Remove this after convert the dependent code to kotlin
fun startsWith(array: Array<String>, text: String) = array.any { text.startsWith(it)}

//TODO: Remove this after convert the dependent code to kotlin
fun containsName(list: List<String>, text: String) = list.indexOfLast { it.endsWith(text) }

/**
 * Get the formatted current playback speed in the form of 1.00x
 */
fun Float.formatRateString() = String.format(java.util.Locale.US, "%.2fx", this)

fun Long.readableFileSize(): String {
    val size: Long = this
    if (size <= 0) return "0"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun Long.readableSize(): String {
    val size: Long = this
    if (size <= 0) return "0"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1000.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun String.removeFileProtocole(): String {
    return if (this.startsWith("file://"))
        this.substring(7)
    else
        this
}

fun String.getFileNameFromPath() = substringBeforeLast('/')

fun String.firstLetterUppercase(): String {
    if (isEmpty()) {
        return ""
    }
    return if (length == 1) {
        toUpperCase(Locale.getDefault())
    } else Character.toUpperCase(this[0]) + substring(1).toLowerCase(Locale.getDefault())
}