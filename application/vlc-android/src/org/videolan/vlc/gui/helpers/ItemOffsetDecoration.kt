package org.videolan.vlc.gui.helpers

import android.content.res.Resources
import android.graphics.Rect
import android.view.View

import androidx.annotation.DimenRes
import androidx.recyclerview.widget.RecyclerView

// Extracted from https://gist.github.com/yqritc/ccca77dc42f2364777e1
class ItemOffsetDecoration(
        private val offsetLeftRight: Int,
        private val offsetTopBottom: Int
) : RecyclerView.ItemDecoration() {

    constructor(
            resources: Resources,
            @DimenRes offsetLeftRightId: Int,
            @DimenRes offsetTopBottomId: Int
    ) : this(resources.getDimensionPixelSize(offsetLeftRightId), resources.getDimensionPixelSize(offsetTopBottomId))

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(offsetLeftRight, offsetTopBottom, offsetLeftRight, offsetTopBottom)
    }
}
