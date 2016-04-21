/*****************************************************************************
 * Thumbnailer.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.media;

import java.lang.Thread.State;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.util.VLCInstance;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Process;
import android.util.Log;

public class Thumbnailer implements Runnable {
    public final static String TAG = "VLC/Thumbnailer";

    private WeakReference<IVideoBrowser> mVideoBrowser;

    private final Queue<MediaWrapper> mItems = new LinkedList<>();

    private boolean isStopping = false;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    protected Thread mThread;
    private int mTotalCount;
    private final String mPrefix;

    public Thumbnailer() {
        mPrefix = VLCApplication.getAppResources().getString(R.string.thumbnail);
    }

    public void start(IVideoBrowser videoBrowser) {
        isStopping = false;
        if (mThread == null || mThread.getState() == State.TERMINATED) {
            mVideoBrowser = new WeakReference<>(videoBrowser);
            mThread = new Thread(this);
            mThread.setPriority(Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            mThread.start();
        }
    }

    public void stop() {
        isStopping = true;
        if (mThread != null)
            mThread.interrupt();
    }

    /**
     * Remove all the thumbnail jobs.
     */
    public void clearJobs() {
        lock.lock();
        try {
            mItems.clear();
            mTotalCount = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gives the count of pending thumbnails
     * @return mTotalCount
     */
    public int getJobsCount(){
        int count;
        lock.lock();
        count = mTotalCount;
        lock.unlock();
        return count;
    }

    /**
     * Add a new media item to create its thumbnail.
     * @param item media wrapper of the file browser item.
     */
    public void addJob(MediaWrapper item) {
        if(BitmapUtil.getPicture(item) != null || item.isPictureParsed())
            return;
        lock.lock();
        try {
            mItems.add(item);
            mTotalCount++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        Log.i(TAG, "Job added!");
    }

    /**
     * Thread main function.
     */
    @Override
    public void run() {
        int total, count = 0;

        Log.d(TAG, "Thumbnailer started");
        mainloop:
        while (!isStopping) {
            lock.lock();
            // Get the id of the file browser item to create its thumbnail.
            while (mItems.size() == 0) {
                try {
                    if (mVideoBrowser != null && mVideoBrowser.get() != null) {
                        mVideoBrowser.get().hideProgressBar();
                        mVideoBrowser.get().clearTextInfo();
                    }
                    mTotalCount = 0;
                    notEmpty.await();
                } catch (InterruptedException e) {
                    Log.i(TAG, "interruption probably requested by stop()");
                    lock.unlock();
                    break mainloop;
                }
            }
            total = mTotalCount;
            MediaWrapper item = mItems.poll();
            lock.unlock();

            if (mVideoBrowser != null && mVideoBrowser.get() != null) {
                mVideoBrowser.get().showProgressBar();
                mVideoBrowser.get().sendTextInfo(String.format("%s %s", mPrefix, item.getFileName()), count, total);
            }
            count++;
            if (item.getArtworkURL() != null)
                continue; //no need for thumbnail, we have a cover

            int width = (VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width));
            int height = (VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_height));

            //Get bitmap
            byte[] b = VLCUtil.getThumbnail(VLCInstance.get(), item.getUri(), width, height);

            if (b == null) {// We were not able to create a thumbnail for this item, store a dummy
                MediaDatabase.setPicture(item, Bitmap.createBitmap(1, 1, Config.ARGB_8888));
                continue;
            }

            // Create the bitmap
            Bitmap thumbnail = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            thumbnail.copyPixelsFromBuffer(ByteBuffer.wrap(b));

            Log.i(TAG, "Thumbnail created for " + item.getFileName());

            MediaDatabase.setPicture(item, thumbnail);
            // Post to the file browser the new item.
            if (mVideoBrowser != null && mVideoBrowser.get() != null) {
                mVideoBrowser.get().setItemToUpdate(item);
            }
        }
        /* cleanup */
        if (mVideoBrowser != null && mVideoBrowser.get() != null) {
            mVideoBrowser.get().hideProgressBar();
            mVideoBrowser.get().clearTextInfo();
            mVideoBrowser.clear();
        }
        Log.d(TAG, "Thumbnailer stopped");
    }

    public void setVideoBrowser(IVideoBrowser browser){
        mVideoBrowser = new WeakReference<>(browser);
    }
}
