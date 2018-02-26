package org.videolan.vlc.util

import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.media.MediaLibraryItem


class FilterDelagate<T : MediaLibraryItem>(private val dataset: MutableLiveData<MutableList<T>>) {
    private var sourceSet: MutableList<T>? = null

    private fun initSource() : MutableList<T>? {
        if (sourceSet === null) sourceSet = (dataset.value)
        return sourceSet
    }

    suspend fun filter(charSequence: CharSequence?) = publish(filteringJob(charSequence))


    private suspend fun filteringJob(charSequence: CharSequence?) : MutableList<T> {
        if (charSequence !== null) initSource()?.let {
            return async { mutableListOf<T>().apply {
                val queryStrings = charSequence.trim().toString().split(" ").filter { it.length > 2 }
                for (item in it) {
                    for (query in queryStrings)
                        if (item.title.contains(query, true)) {
                            this.add(item)
                            break
                        }
                } }
            }.await()
        }
        return mutableListOf()
    }

    private fun publish(list: MutableList<T>?) {
        sourceSet?.let {
            if (list?.isEmpty() == false)
                dataset.value = list
            else {
                dataset.value = it
                sourceSet = null
            }
        }
    }
}