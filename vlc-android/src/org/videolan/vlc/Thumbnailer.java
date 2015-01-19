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

package org.videolan.vlc;

import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.BitmapUtil;
import org.videolan.vlc.util.VLCInstance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

public class Thumbnailer implements Runnable {
    public final static String TAG = "VLC/Thumbnailer";

    private IVideoBrowser mVideoBrowser;

    private final Queue<MediaWrapper> mItems = new LinkedList<MediaWrapper>();

    private boolean isStopping = false;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    protected Thread mThread;
    private LibVLC mLibVlc;
    private int totalCount;
    private final float mDensity;
    private final String mPrefix;

    public Thumbnailer(Context context, Display display) {
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mDensity = metrics.density;
        mPrefix = context.getResources().getString(R.string.thumbnail);
    }

    public void start(IVideoBrowser videoBrowser) {
        if (mLibVlc == null) {
            try {
                mLibVlc = VLCInstance.getLibVlcInstance();
            } catch (LibVlcException e) {
                Log.e(TAG, "Can't obtain libvlc instance");
                e.printStackTrace();
                return;
            }
        }

        isStopping = false;
        if (mThread == null || mThread.getState() == State.TERMINATED) {
            mVideoBrowser = videoBrowser;
            mThread = new Thread(this);
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
        mItems.clear();
        totalCount = 0;
        lock.unlock();
    }

    /**
     * Gives the count of pending thumbnails
     * @return totalCount
     */
    public int getJobsCount(){
        return totalCount;
    }

    /**
     * Add a new media item to create its thumbnail.
     * @param item media wrapper of the file browser item.
     */
    public void addJob(MediaWrapper item) {
        if(BitmapUtil.getPictureFromCache(item) != null || item.isPictureParsed())
            return;
        lock.lock();
        mItems.add(item);
        totalCount++;
        notEmpty.signal();
        lock.unlock();
        Log.i(TAG, "Job added!");
    }

    /**
     * Thread main function.
     */
    @Override
    public void run() {
        int count = 0;
        int total = 0;

        Log.d(TAG, "Thumbnailer started");

        while (!isStopping) {
            if (mVideoBrowser != null)
                mVideoBrowser.resetBarrier();
            lock.lock();
            // Get the id of the file browser item to create its thumbnail.
            boolean interrupted = false;
            while (mItems.size() == 0) {
                try {
                    if (mVideoBrowser != null) {
                        mVideoBrowser.hideProgressBar();
                        mVideoBrowser.clearTextInfo();
                    }
                    totalCount = 0;
                    notEmpty.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                    Log.i(TAG, "interruption probably requested by stop()");
                    break;
                }
            }
            if (interrupted) {
                lock.unlock();
                break;
            }
            total = totalCount;
            MediaWrapper item = mItems.poll();
            lock.unlock();

            if (mVideoBrowser != null) {
                mVideoBrowser.showProgressBar();
                mVideoBrowser.sendTextInfo(String.format("%s %s", mPrefix, item.getFileName()), count, total);
            }
            count++;

            int width = (int) (120 * mDensity);
            int height = (int) (75 * mDensity);

            //Get bitmap
            byte[] b = mLibVlc.getThumbnail(item.getLocation(), width, height);

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
            if (mVideoBrowser != null) {
                mVideoBrowser.setItemToUpdate(item);

                // Wait for the file browser to process the change.
                try {
                    mVideoBrowser.await();
                } catch (InterruptedException e) {
                    Log.i(TAG, "interruption probably requested by stop()");
                    break;
                } catch (BrokenBarrierException e) {
                    Log.e(TAG, "Unexpected BrokenBarrierException");
                    e.printStackTrace();
                    break;
                }
            }
        }
        /* cleanup */
        if (mVideoBrowser != null) {
            mVideoBrowser.hideProgressBar();
            mVideoBrowser.clearTextInfo();
        }
        mVideoBrowser = null;
        Log.d(TAG, "Thumbnailer stopped");
    }

    public void setVideoBrowser(IVideoBrowser browser){
        mVideoBrowser = browser;
    }
}
