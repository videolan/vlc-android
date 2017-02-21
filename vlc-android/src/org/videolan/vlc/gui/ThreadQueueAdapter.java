/*
 * *************************************************************************
 *  ThreadQueueAdapter.java
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

import org.videolan.vlc.VLCApplication;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public abstract class ThreadQueueAdapter<V extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<V> {

    private ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(1, 1, 2, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), VLCApplication.THREAD_FACTORY);

    protected void queueTask(Runnable runnable) {
        mThreadPool.execute(runnable);
    }

}
