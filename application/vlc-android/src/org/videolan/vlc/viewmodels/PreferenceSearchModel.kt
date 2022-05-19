/*
 * ************************************************************************
 *  PreferenceSearchModel.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.gui.preferences.search.PreferenceItem
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import java.util.*

class PreferenceSearchModel(context: Context) : ViewModel() {
    val dataset = LiveDataset<PreferenceItem>()
    val filtered = LiveDataset<PreferenceItem>()
    val showTranslations= MutableLiveData<Boolean>()

    init {
        viewModelScope.launch {
            dataset.value = withContext(Dispatchers.IO) {
                PreferenceParser.parsePreferences(context)
            }
        }
        showTranslations.value = false
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PreferenceSearchModel(context.applicationContext) as T
        }
    }

    /**
     * Filter the preference list with a query. The result is then stored in the [filtered] livedata
     * @param query the query used to filter
     */
    fun filter(query: String) {
        if (query.isBlank())
            filtered.value = arrayListOf()
        else
            filtered.value = dataset.getList().filter {
                        getTitle(it).lowercase(Locale.getDefault()).contains(query)
                        || getSummary(it).lowercase(Locale.getDefault()).contains(query)
            }.sortedWith { i0, i1 ->
                score(i1, query) - score(i0, query)
            }.toMutableList()
    }

    fun getSummary(item: PreferenceItem)= if (showTranslations.value == true) item.summaryEng else item.summary
    fun getTitle(item: PreferenceItem)= if (showTranslations.value == true) item.titleEng else item.title

    /**
     * Determinate a score for [item] to sort the results for a [query]:
     * - Having a word starting with [query] in [item] title grants 1000 points
     * - Having a word starting with [query] in [item] description grants 100 points
     * - [item] title contains [query] grants 10 points
     *
     * @param item: the item to calculate to score for
     * @param query: the query used to calculate the score
     *
     * @return a score
     */
    private fun score(item: PreferenceItem, query: String): Int {
        var score = 0
        if (getSummary(item).lowercase(Locale.getDefault()).contains(query)) score += 1
        if (getTitle(item).lowercase(Locale.getDefault()).contains(query)) score += 10
        if (getSummary(item).lowercase(Locale.getDefault()).split(" ").any { it.startsWith(query) }) score += 100
        if (getTitle(item).lowercase(Locale.getDefault()).split(" ").any { it.startsWith(query) }) score += 1000
        return score
    }

    fun switchTranslations(query: String) {
        showTranslations.value = showTranslations.value == false
        filter(query)
    }
}
