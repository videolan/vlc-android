/*****************************************************************************
 * BaseQueuedAdapter.java
 *****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui;

import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public abstract class BaseQueuedAdapter <T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    protected final ExecutorService mUpdateExecutor = Executors.newSingleThreadExecutor();

    protected volatile ArrayList<T> mDataset = new ArrayList<>();
    private final ArrayDeque<ArrayList<T>> mPendingUpdates = new ArrayDeque<>();

    protected abstract void internalUpdate(ArrayList<T> items, boolean detectMoves);
    protected abstract void onUpdateFinished();

    @MainThread
    public boolean hasPendingUpdates() {
        return !mPendingUpdates.isEmpty();
    }

    @MainThread
    public int getPendingCount() {
        return mPendingUpdates.size();
    }

    @MainThread
    public ArrayList<T> peekLast() {
        return mPendingUpdates.isEmpty() ? mDataset : mPendingUpdates.peekLast();
    }

    @MainThread
    public void update(final ArrayList<T> items) {
        update(items, false);
    }

    @MainThread
    public void update(final ArrayList<T> items, boolean detectMoves) {
        mPendingUpdates.add(items);
        if (mPendingUpdates.size() == 1)
            internalUpdate(items, detectMoves);
    }

    @MainThread
    protected void processQueue() {
        mPendingUpdates.remove();
        if (mPendingUpdates.isEmpty())
            onUpdateFinished();
        else {
            if (mPendingUpdates.size() > 1) {
                ArrayList<T> lastList = mPendingUpdates.peekLast();
                mPendingUpdates.clear();
                update(lastList);
            } else
                internalUpdate(mPendingUpdates.peek(), false);
        }
    }
}
