/*****************************************************************************
 * MediaComparators.java
 *
 * Copyright © 2013 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.helpers

import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import java.text.Normalizer
import java.util.*

object MediaComparators {

    private val englishArticles by lazy { arrayOf("a ", "an ", "the ") }
    private val asciiAlphaNumeric by lazy {
        BitSet().also { b -> "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray().forEach { c -> b.set(c.code, true) } }
    }
    private val asciiPunctuation by lazy {
        BitSet().also { b -> "\t !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toCharArray().forEach { c -> b.set(c.code, true) } }
    }

    val BY_TRACK_NUMBER: Comparator<MediaWrapper> = Comparator { m1, m2 ->
        if (m1.discNumber < m2.discNumber) return@Comparator -1
        if (m1.discNumber > m2.discNumber) return@Comparator 1
        if (m1.trackNumber < m2.trackNumber) return@Comparator -1
        if (m1.trackNumber > m2.trackNumber)
            1
        else
            0
    }

    val ANDROID_AUTO: Comparator<MediaLibraryItem> = Comparator { item1, item2 ->
        buildComparableTitle(item1.title).compareTo(buildComparableTitle(item2.title))
    }

    private fun buildComparableTitle(origTitle: String): String {
        val tTitle = origTitle.trim()
        if (tTitle.isEmpty()) return tTitle
        /* Remove invalid leading characters and articles */
        val invCharTitle = removeLeadingPunctuation(tTitle).lowercase(Locale.US)
        val scrubbedTitle = formatArticles(invCharTitle, false).ifEmpty { invCharTitle }
        /* Decompose the first letter to handle ä, ç, é, ô, etc. This yields two chars: an a-z letter, and
         * a unicode combining character representing the diacritic mark. The combining character is dropped.
         */
        val nChars = Normalizer.normalize(scrubbedTitle[0].toString(), Normalizer.Form.NFD)
        val firstChar = removeNonAlphaNumeric(nChars).ifEmpty { nChars }
        /* Assemble the title */
        return "${firstChar[0]}${scrubbedTitle.substring(1)}"
    }

    /**
     * Functionally identical to "^(?i)(the|an|a)\\s+(.*)", and either returning "$2" or "$2, $1"
     */
    fun formatArticles(title: String, appendPrefix: Boolean): String {
        for (article in englishArticles)
            if (title.startsWith(article, true)) {
                val suffix = title.substring(article.length).trim()
                return if (appendPrefix) {
                    val prefix = title.substring(0, article.length).trim()
                    listOf(suffix, prefix).filter { it.isNotEmpty() }.joinToString(", ") { it }
                } else suffix
            }
        return title
    }

    /**
     * Remove all non-lowercase alphanumeric characters.
     * Functionally identical to replaceAll("[^a-z0-9]+", "")
     */
    private fun removeNonAlphaNumeric(title: String): String {
        return if (title.length == 1 && asciiAlphaNumeric.get(title[0].code)) title
        else buildString {
            for (c in title.toCharArray())
                if (asciiAlphaNumeric.get(c.code))
                    append(c)
        }
    }

    /**
     * Find the first occurrence of non-punctuation characters.
     * Functionally identical to replaceAll("^[\t\\x20-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E]+", "")
     * "[On Android]...Unicode character classes are always used." (see Pattern.UNICODE_CHARACTER_CLASS)
     * therefore "^[\\p{Blank}\\p{Punct}]+" is not a direct substitute.
     */
    private fun removeLeadingPunctuation(title: String): String {
        title.forEachIndexed { i, c ->
            if (!asciiPunctuation.get(c.code))
                return title.substring(i)
        }
        return title
    }
}

