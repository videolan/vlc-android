package org.videolan.vlc.gui.tv

interface TvItemAdapter : TvFocusableAdapter {
    fun submitList(pagedList: Any?)
    fun isEmpty() : Boolean
    var focusNext: Int
}
