package org.videolan.tools

import androidx.annotation.MainThread
import kotlin.math.max
import kotlin.math.min


class MultiSelectHelper<T>(val adapter: MultiSelectAdapter<T>, private val payloadvalue: Any = 0) {

    val selectionMap = ArrayList<Int>()
    var inActionMode = false

    fun getSelection(): List<T> {
        val list = ArrayList<T>(selectionMap.size)
        for (i in 0 until selectionMap.size) adapter.getItem(selectionMap[i])?.let { list.add(it) }
        return list
    }

    @MainThread
    fun getSelectionCount() = selectionMap.size

    fun toggleActionMode(inActionMode:Boolean, itemCount:Int) {
        this.inActionMode = inActionMode
        adapter.notifyItemRangeChanged(0, itemCount, payloadvalue)
    }

    fun toggleSelection(position: Int, forceShift: Boolean = false) {
        if ((KeyHelper.isShiftPressed || forceShift) && selectionMap.size != 0) {
            val positions = HashSet<Int>()
            for (i in 0 until selectionMap.size) {
                positions.add(selectionMap[i])
            }
            val firstPosition = selectionMap[0]
            selectionMap.clear()

            for (i in min(firstPosition, position)..max(firstPosition, position)) {
                selectionMap.add(i)
                positions.add(i)
            }

            positions.forEach {
                adapter.notifyItemChanged(it, payloadvalue)
            }
            return
        }
        if (isSelected(position)) selectionMap.remove(position)
        else selectionMap.add(position)
        adapter.notifyItemChanged(position, payloadvalue)
    }

    fun clearSelection() {
        if (selectionMap.size == 0) return
        val start = selectionMap.minOrNull()
        val count = selectionMap.maxOrNull()
        selectionMap.clear()
        adapter.notifyItemRangeChanged(start ?: 0, count ?: 0, payloadvalue)
    }

    fun isSelected(position: Int) = selectionMap.contains(position)
}

interface MultiSelectAdapter<T> {
    fun getItem(position: Int): T?
    fun notifyItemChanged(start: Int, payload: Any?)
    fun notifyItemRangeChanged(start: Int, count: Int, payload: Any?)
}