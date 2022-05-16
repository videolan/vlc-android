/*
 * ************************************************************************
 *  TextUtils.kt
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

package org.videolan.vlc.util

import android.content.Context
import org.videolan.vlc.R

object TextUtils {

    /**
     * Common string separator used in the whole app
     */
    const val separator = '·'

    /**
     * Create a string separated by the common [separator]
     *
     * @param pieces the strings to join
     * @return a string containing all the [pieces] if they are not blanked, spearated by the [separator]
     */
    @JvmName("separatedStringArgs")
    fun separatedString(vararg pieces: String?) = separatedString(arrayOf(*pieces))

    /**
     * Create a string separated by the common [separator]
     *
     * @param pieces the strings to join in an [Array]
     * @return a string containing all the [pieces] if they are not blanked, spearated by the [separator]
     */
    fun separatedString(pieces: Array<String?>) = pieces.filter { it?.isNotBlank() == true }.joinToString(separator = " $separator ")

    /**
     * Formats the chapter title by prepending "Chapter:" if the current title is made of only non alpha chars
     *
     * @param context the context to use to retrieve the string
     * @param title the title to format
     * @return a formatted string
     */
    fun formatChapterTitle(context: Context, title: String?) = if (title?.firstOrNull { it.isLetter() } == null) context.getString(R.string.current_chapter, title) else title

}