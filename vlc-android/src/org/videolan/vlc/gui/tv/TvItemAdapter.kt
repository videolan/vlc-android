package org.videolan.vlc.gui.tv

interface TvItemAdapter : TvFocusableAdapter {
    fun submitList(pagedList: Any?)

    var focusNext: Int
}
