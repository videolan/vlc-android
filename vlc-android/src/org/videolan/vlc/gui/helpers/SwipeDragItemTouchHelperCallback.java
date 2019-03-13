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

package org.videolan.vlc.gui.helpers;

import org.jetbrains.annotations.NotNull;
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwipeDragItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private static final String TAG = SwipeDragItemTouchHelperCallback.class.getSimpleName();
    private final SwipeDragHelperAdapter mAdapter;
    private int dragFrom = -1;
    private int dragTo = -1;

    public SwipeDragItemTouchHelperCallback(SwipeDragHelperAdapter mAdapter) {
        this.mAdapter = mAdapter;
    }

    @Override
    public int getMovementFlags(@NotNull RecyclerView recyclerView, @NotNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NotNull RecyclerView recyclerView, @NotNull RecyclerView.ViewHolder viewHolder, @NotNull RecyclerView.ViewHolder target) {
        mAdapter.onItemMove(viewHolder.getLayoutPosition(), target.getLayoutPosition());
        int fromPosition = viewHolder.getLayoutPosition();
        int toPosition = target.getLayoutPosition();


        if(dragFrom == -1) {
            dragFrom =  fromPosition;
        }
        dragTo = toPosition;

        return true;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public void onMoved(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos, @NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        if(dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            mAdapter.onItemMoved(dragFrom, dragTo);
        }

        dragFrom = dragTo = -1;
        super.clearView(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(@NotNull RecyclerView.ViewHolder viewHolder, int direction) {
        mAdapter.onItemDismiss(viewHolder.getLayoutPosition());
    }
}
