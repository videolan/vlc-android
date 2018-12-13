package org.videolan.tools

import android.util.SparseBooleanArray
import androidx.annotation.MainThread


class MultiSelectHelper<T>(val adapter: MultiSelectAdapter<T>, private val payloadvalue : Any = 0) {

    val selectionMap = SparseBooleanArray()

    fun getSelection(): List<T> {
        val list = ArrayList<T>(selectionMap.size())
        for (i in 0 until selectionMap.size()) adapter.getItem(selectionMap.keyAt(i))?.let { list.add(it) }
        return list
    }

    @MainThread
    fun getSelectionCount() = selectionMap.size()

    fun toggleSelection(position: Int) {
        if (isSelected(position)) selectionMap.delete(position)
        else selectionMap.append(position, true)
        adapter.notifyItemChanged(position, payloadvalue)
    }

    fun clearSelection() {
        if (selectionMap.size() == 0) return
        val start = selectionMap.keyAt(0)
        val count = selectionMap.keyAt(selectionMap.size() - 1) - start + 1
        selectionMap.clear()
        adapter.notifyItemRangeChanged(start, count, payloadvalue)
    }

    fun isSelected(position: Int) = selectionMap.get(position, false)
}

interface MultiSelectAdapter<T> {
    fun getItem(position: Int) : T?
    fun notifyItemChanged(start: Int, payload: Any?)
    fun notifyItemRangeChanged(start: Int, count: Int, payload: Any?)
}