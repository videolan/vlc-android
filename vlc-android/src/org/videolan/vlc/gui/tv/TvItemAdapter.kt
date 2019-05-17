package org.videolan.vlc.gui.tv

interface TvItemAdapter {
    fun submitList(pagedList: Any?)
    fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?)


    var focusNext: Int
}