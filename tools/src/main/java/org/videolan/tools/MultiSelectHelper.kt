package org.videolan.tools

import androidx.annotation.MainThread
import android.util.SparseBooleanArray


class MultiSelectHelper<T>(val adapter: MultiSelectAdapter<T>, private val payloadvalue : Any = 0) {

    private val selection = SparseBooleanArray()

    fun getSelection(): List<T> {
        val list = ArrayList<T>(selection.size())
        for (i in 0 until selection.size()) list.add(adapter.getItem(selection.keyAt(i)))
        return list
    }

    @MainThread
    fun getSelectionCount() = selection.size()

    fun toggleSelection(position: Int) {
        if (isSelected(position)) selection.delete(position)
        else selection.append(position, true)
        adapter.notifyItemChanged(position, payloadvalue)
    }

    fun clearSelection() {
        if (selection.size() == 0) return
        val start = selection.keyAt(0)
        val count = selection.keyAt(selection.size() - 1) - start + 1
        selection.clear()
        adapter.notifyItemRangeChanged(start, count, payloadvalue)
    }

    fun isSelected(position: Int) = selection.get(position, false)
}

interface MultiSelectAdapter<T> {
    fun getItem(position: Int) : T
    fun notifyItemChanged(start: Int, payload: Any?)
    fun notifyItemRangeChanged(start: Int, count: Int, payload: Any?)
}