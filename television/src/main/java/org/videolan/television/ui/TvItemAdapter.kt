package org.videolan.television.ui

interface TvItemAdapter : org.videolan.television.ui.TvFocusableAdapter {
    fun submitList(pagedList: Any?)
    fun isEmpty() : Boolean
    var focusNext: Int
}
