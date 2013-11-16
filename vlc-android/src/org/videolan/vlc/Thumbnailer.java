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
import org.videolan.libvlc.Media;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.video.VideoGridFragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

public class Thumbnailer implements Runnable {
    public final static String TAG = "VLC/Thumbnailer";

    private VideoGridFragment mVideoGridFragment;

    private final Queue<Media> mItems = new LinkedList<Media>();

    private boolean isStopping = false;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    protected Thread mThread;
    private LibVLC mLibVlc;
    private final Context mContext;
    private int totalCount;
    private final float mDensity;
    private final String mPrefix;

    public Thumbnailer(Context context, Display display) {
        mContext = context;
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mDensity = metrics.density;
        mPrefix = mContext.getResources().getString(R.string.thumbnail);
    }

    public void start(VideoGridFragment videoGridFragment) {
        if (mLibVlc == null) {
            try {
                mLibVlc = Util.getLibVlcInstance();
            } catch (LibVlcException e) {
                Log.e(TAG, "Can't obtain libvlc instance");
                e.printStackTrace();
                return;
            }
        }

        isStopping = false;
        if (mThread == null || mThread.getState() == State.TERMINATED) {
            mVideoGridFragment = videoGridFragment;
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
     * Add a new id of the file browser item to create its thumbnail.
     * @param id the if of the file browser item.
     */
    public void addJob(Media item) {
        if(Util.getPictureFromCache(item) != null || item.isPictureParsed())
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
            mVideoGridFragment.resetBarrier();
            lock.lock();
            // Get the id of the file browser item to create its thumbnail.
            boolean interrupted = false;
            while (mItems.size() == 0) {
                try {
                    MainActivity.hideProgressBar(mContext);
                    MainActivity.clearTextInfo(mContext);
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
            Media item = mItems.poll();
            lock.unlock();

            MainActivity.showProgressBar(mContext);

            MainActivity.sendTextInfo(mContext, String.format("%s %s", mPrefix, item.getFileName()), count, total);
            count++;

            int width = (int) (120 * mDensity);
            int height = (int) (75 * mDensity);

            // Get the thumbnail.
            Bitmap thumbnail = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            //Log.i(TAG, "create new bitmap for: " + item.getName());
            byte[] b = mLibVlc.getThumbnail(item.getLocation(), width, height);

            if (b == null) {// We were not able to create a thumbnail for this item, store a dummy
                Util.setPicture(mContext, item, Bitmap.createBitmap(1, 1, Config.ARGB_8888));
                continue;
            }

            thumbnail.copyPixelsFromBuffer(ByteBuffer.wrap(b));

            Log.i(TAG, "Thumbnail created for " + item.getFileName());

            Util.setPicture(mContext, item, thumbnail);
            // Post to the file browser the new item.
            mVideoGridFragment.setItemToUpdate(item);

            // Wait for the file browser to process the change.
            try {
                mVideoGridFragment.await();
            } catch (InterruptedException e) {
                Log.i(TAG, "interruption probably requested by stop()");
                break;
            } catch (BrokenBarrierException e) {
                Log.e(TAG, "Unexpected BrokenBarrierException");
                e.printStackTrace();
                break;
            }
        }
        /* cleanup */
        MainActivity.hideProgressBar(mContext);
        MainActivity.clearTextInfo(mContext);
        mVideoGridFragment = null;
        Log.d(TAG, "Thumbnailer stopped");
    }
}
