/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui;

import android.support.v7.widget.RecyclerView;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.videolan.vlc.VLCApplication.THREAD_FACTORY;


public abstract class BaseAdapter<V extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<V> {

    private static final String TAG = "VLC/BaseAdapter";

    private ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(0, 1, 2, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), THREAD_FACTORY);

    private boolean mDatasetLocked = false;

    protected boolean acquireDatasetLock() {
        synchronized (BaseAdapter.this) {
            if (mDatasetLocked) {
                try {
                    BaseAdapter.this.wait(1000);
                } catch (InterruptedException ignored) {
                    return false;
                }
            }
            mDatasetLocked = true;
        }
        return true;
    }

    protected void releaseDatasetLock() {
        synchronized (BaseAdapter.this) {
            mDatasetLocked = false;
            BaseAdapter.this.notify();
        }
    }

    protected void queueBackground(Runnable runnable, boolean clear) {
        if (clear)
            mThreadPool.getQueue().clear();
        mThreadPool.execute(runnable);
    }
}
