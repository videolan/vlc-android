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
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "VLC/UiTools/Strings"

fun String.stripTrailingSlash() = if (endsWith("/") && length > 1) dropLast(1) else this
fun String.addTrailingSlashIfNeeded() = if (endsWith("/")) this else "$this/"

//TODO: Remove this after convert the dependent code to kotlin
fun startsWith(array: Array<String>, text: String) = array.any { text.startsWith(it)}

//TODO: Remove this after convert the dependent code to kotlin
fun containsName(list: List<String>, text: String) = list.indexOfLast { it.endsWith(text) }

/**
 * Get the formatted current playback speed in the form of 1.00x
 */
fun Float.formatRateString() = String.format(java.util.Locale.US, "%.2fx", this)

fun Long.readableSize(): String {
    val size: Long = this
    if (size <= 0) return "0"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / (1000.0).pow(digitGroups.toDouble())) + " " + units[digitGroups]
}

fun String.removeFileScheme() = if (this.startsWith("file://")) this.drop(7) else this

fun String.getFileNameFromPath() = substringBeforeLast('/')

fun String.firstLetterUppercase(): String {
    if (isEmpty()) {
        return ""
    }
    return if (length == 1) {
        uppercase(Locale.getDefault())
    } else Character.toUpperCase(this[0]) + substring(1).lowercase(Locale.getDefault())
}

fun String.password() =  "*".repeat(length)

fun String.abbreviate(maxLen: Int): String {
    val ellipsis = "\u2026"
    val trimmed = this.trim()
    return if (trimmed.length > maxLen) trimmed.take(maxLen - 1).trim().plus(ellipsis)
    else trimmed
}

fun String.markBidi(): String {
    //right-to-left isolate
    val rli = "\u2067"
    //pop directional isolate
    val pdi = "\u2069"
    for (ch in this) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return rli + this + pdi
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return this
        }
    }
    return this
}