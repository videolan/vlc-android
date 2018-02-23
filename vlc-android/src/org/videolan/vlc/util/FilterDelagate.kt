package org.videolan.vlc.util

import android.arch.lifecycle.MutableLiveData
import org.videolan.medialibrary.media.MediaLibraryItem


class FilterDelagate<T : MediaLibraryItem>(private val dataset: MutableLiveData<MutableList<T>>) {
    private var sourceSet: MutableList<T>? = null

    private fun initSource() : MutableList<T>? {
        if (sourceSet === null) sourceSet = (dataset.value)
        return sourceSet
    }

    fun filter(charSequence: CharSequence?) = publish(filteringJob(charSequence))


    private fun filteringJob(charSequence: CharSequence?) : MutableList<T> {
        if (charSequence !== null) initSource()?.let {
            val list = mutableListOf<T>()
            val queryStrings = charSequence.trim().toString().split(" ").filter { it.length > 2 }
            for (item in it) {
                for (query in queryStrings)
                    if (item.title.contains(query, true)) {
                        list.add(item)
                        break
                    }
            }
            return list
        }
        return mutableListOf()
    }

    private fun publish(list: MutableList<T>?) {
        sourceSet?.let {
            if (list?.isEmpty() == false)
                dataset.postValue(list)
            else {
                dataset.postValue(it)
                sourceSet = null
            }
        }
    }
}