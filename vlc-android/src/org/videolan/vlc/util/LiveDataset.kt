package org.videolan.vlc.util

import android.arch.lifecycle.MutableLiveData


class LiveDataset<T> : MutableLiveData<MutableList<T>>() {

    private val emptyList = mutableListOf<T>()

    override fun getValue(): MutableList<T> {
        return super.getValue() ?: emptyList
    }

    fun clear() {
        value = value.apply { clear() }
    }

    fun add(item: T) {
        value = value.apply { add(item) }
    }

    fun add(items: List<T>) {
        value = value.apply { addAll(items.filter { !this.contains(it) }) }
    }

    fun remove(item: T) {
        value = value.apply { remove(item) }
    }
}