/*
 * *************************************************************************
 *  SwipeDragItemTouchHelperCallback.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.gui.helpers.hf.PinCodeDelegate
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter

class SwipeDragItemTouchHelperCallback(private val mAdapter: SwipeDragHelperAdapter, private val longPressDragEnable: Boolean = false, private val lockedInSafeMode: Boolean = false) : ItemTouchHelper.Callback() {
    private var dragFrom = -1
    private var dragTo = -1
    var swipeEnabled = true
    var swipeAttemptListener: (() -> Unit)? = null

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (lockedInSafeMode && PinCodeDelegate.pinUnlocked.value == false) {
            swipeAttemptListener?.invoke()
            return false
        }
        mAdapter.onItemMove(viewHolder.layoutPosition, target.layoutPosition)
        val fromPosition = viewHolder.layoutPosition
        val toPosition = target.layoutPosition


        if (dragFrom == -1) {
            dragFrom = fromPosition
        }
        dragTo = toPosition

        return true
    }

    override fun isLongPressDragEnabled(): Boolean {
        return longPressDragEnable
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            mAdapter.onItemMoved(dragFrom, dragTo)
        }

        dragTo = -1
        dragFrom = dragTo
        super.clearView(recyclerView, viewHolder)
    }

    override fun isItemViewSwipeEnabled() = swipeEnabled

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        mAdapter.onItemDismiss(viewHolder.layoutPosition)
    }
}
