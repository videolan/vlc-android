package org.videolan.television.ui

interface TvItemAdapter : TvFocusableAdapter {
    fun submitList(pagedList: Any?)
    fun isEmpty() : Boolean
    var focusNext: Int
    fun displaySwitch(inGrid: Boolean)
}
