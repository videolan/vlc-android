package org.videolan.vlc.util

import androidx.lifecycle.MutableLiveData


class LiveDataset<T> : MutableLiveData<MutableList<T>>() {

    private val emptyList = mutableListOf<T>()

    private var internalList = emptyList

    fun isEmpty() = internalList.isEmpty()

    override fun setValue(value: MutableList<T>?) {
        internalList = value ?: emptyList
        super.setValue(value)
    }

    override fun getValue() = super.getValue() ?: emptyList

    fun get(position: Int) = internalList[position]

    fun getList() = internalList.toList()

    fun clear() {
        value = internalList.apply { clear() }
    }

    fun add(item: T) {
        value = internalList.apply { add(item) }
    }

    fun add(position: Int, item: T) {
        value = internalList.apply { add(position, item) }
    }

    fun add(items: List<T>) {
        value = internalList.apply { addAll(items.filter { !this.contains(it) }) }
    }

    fun remove(item: T) {
        value = internalList.apply { remove(item) }
    }

    fun remove(position: Int) {
        value = internalList.apply { removeAt(position) }
    }
}