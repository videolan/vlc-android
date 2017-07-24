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
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.MediaItemDiffCallback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseQueuedAdapter <T extends MediaLibraryItem, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    protected final ExecutorService mUpdateExecutor = Executors.newSingleThreadExecutor();

    protected volatile ArrayList<T> mDataset = new ArrayList<>();
    private final ArrayDeque<ArrayList<T>> mPendingUpdates = new ArrayDeque<>();

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
        mPendingUpdates.add(items);
        if (mPendingUpdates.size() == 1)
            internalUpdate(items);
    }

    @MainThread
    private void internalUpdate(final ArrayList<T> newList) {
        mUpdateExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final ArrayList<T> finalList = prepareList(newList);
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(createCB(finalList), detectMoves());
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mDataset = finalList;
                        result.dispatchUpdatesTo(BaseQueuedAdapter.this);
                        processQueue();
                    }
                });
            }
        });

    }

    protected boolean detectMoves() {
        return false;
    }

    protected ArrayList<T> prepareList(ArrayList<T> list) {
        return list;
    }

    @MainThread
    private void processQueue() {
        mPendingUpdates.remove();
        if (mPendingUpdates.isEmpty())
            onUpdateFinished();
        else {
            ArrayList<T> lastList = mPendingUpdates.peekLast();
            if (!mPendingUpdates.isEmpty()) {
                mPendingUpdates.clear();
                mPendingUpdates.add(lastList);
            }
            internalUpdate(lastList);
        }
    }

    protected DiffUtil.Callback createCB(final ArrayList<T> items) {
        return new MediaItemDiffCallback(mDataset, items);
    }
}
