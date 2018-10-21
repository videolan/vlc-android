package org.videolan.vlc.gui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.util.AttributeSet
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import androidx.appcompat.widget.AppCompatSpinner


class MultiSelectionSpinner : AppCompatSpinner, OnMultiChoiceClickListener, DialogInterface.OnDismissListener{

    private var items = mutableListOf<String>()
    private var selection = mutableListOf<Boolean>()
    private var adapter: ArrayAdapter<String>
    private var listener: org.videolan.vlc.gui.OnItemSelectListener? = null

    val selectedIndices: List<Int>
        get() {
            return selection.mapIndexed { index, b -> Pair(index, b)}.filter { it.second }.map { it.first }
        }

    constructor(context: Context) : super(context) {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item)
        super.setAdapter(adapter)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item)
        super.setAdapter(adapter)
    }

    override fun onClick(dialog: DialogInterface, index: Int, isChecked: Boolean) {
        if (index < selection.size) {
            selection[index] = isChecked

            adapter.clear()
            adapter.add(buildSelectedItemString())
        } else {
            throw IllegalArgumentException(
                    "Argument 'index' is out of bounds.")
        }
    }

    override fun performClick(): Boolean {
        val builder = AlertDialog.Builder(context)
        builder.setOnDismissListener(this)
        builder.setMultiChoiceItems(items.toTypedArray(), selection.toBooleanArray(), this).show()
        return true
    }

    override fun onDismiss(dialog: DialogInterface?) {
        listener?.onItemSelect(selectedIndices)
    }

    override fun setAdapter(adapter: SpinnerAdapter) {
        throw RuntimeException(
                "setAdapter is not supported by MultiSelectSpinner. Use setItems instead")
    }

    fun setItems(items: List<String>) {
        this.items = items.toMutableList()
        adapter.clear()
        adapter.add(items[0])
        selection.addAll(items.map{false})
    }

    override fun setSelection(index: Int) {
        selection = selection.map { false }.toMutableList()

        if (index >= 0 && index < selection.size) selection[index] = true
        else throw IllegalArgumentException("Index $index is out of bounds.")
        adapter.clear()
        adapter.add(buildSelectedItemString())
        listener?.onItemSelect(selectedIndices)
    }

    fun setSelection(selectedIndices: List<Int>) {
        selection = selection.map { false }.toMutableList()
        selectedIndices.forEach {
            if (it >= 0 && it < selection.size) selection[it] = true
            else throw IllegalArgumentException("Index $it is out of bounds.")
        }
        adapter.clear()
        adapter.add(buildSelectedItemString())
        listener?.onItemSelect(selectedIndices)
    }

    private fun buildSelectedItemString(): String {
        val sb = StringBuilder()

        val selectedItems = items.filterIndexed { index, _ -> selection[index] }

        if (selectedItems.isEmpty() || selectedItems.size == items.size) sb.append("All")
        else selectedItems.forEachIndexed { index, s ->
            if (index > 0) sb.append(',')
            sb.append(s)
        }
        return sb.toString()
    }

    fun setOnItemsSelectListener(l: org.videolan.vlc.gui.OnItemSelectListener) {
        listener = l
    }
}

interface OnItemSelectListener {
    fun onItemSelect(selectedItems: List<Int>)
}

