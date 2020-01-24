package org.videolan.television.ui

import org.videolan.resources.interfaces.FocusListener

/**
 * Callback used when the adapter is used in a [FocusableRecyclerView]
 */
interface TvFocusableAdapter {
    fun setOnFocusChangeListener(focusListener: FocusListener?)
}