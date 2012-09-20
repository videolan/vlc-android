/*****************************************************************************
 * ThumbnailerManager.java
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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.video.VideoListFragment;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.DisplayMetrics;
import android.util.Log;

public class ThumbnailerManager extends Thread {
    public final static String TAG = "VLC/ThumbnailerManager";

    private final Queue<Media> mItems = new LinkedList<Media>();

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    private LibVLC mLibVlc;
    private final VideoListFragment mVideoListActivity;
    private int totalCount;
    private final float mDensity;

    public ThumbnailerManager(VideoListFragment videoListActivity) {
        mVideoListActivity = videoListActivity;
        try {
            mLibVlc = LibVLC.getInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        DisplayMetrics metrics = new DisplayMetrics();
        mVideoListActivity.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDensity = metrics.density;
        start();
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

        String prefix = mVideoListActivity.getResources().getString(R.string.thumbnail);

        while (!isInterrupted()) {
            lock.lock();
            // Get the id of the file browser item to create its thumbnail.
            boolean killed = false;
            while (mItems.size() == 0) {
                try {
                    Log.i(TAG, "hide ProgressBar!");
                    MainActivity.hideProgressBar(mVideoListActivity.getActivity());
                    MainActivity.clearTextInfo(mVideoListActivity.getActivity());
                    notEmpty.await();
                } catch (InterruptedException e) {
                    killed = true;
                    break;
                }
            }
            if (killed)
                break;
            total = totalCount;
            Media item = mItems.poll();
            lock.unlock();

            MainActivity.showProgressBar(mVideoListActivity.getActivity());

            Log.i(TAG, "show ProgressBar!");
            MainActivity.sendTextInfo(mVideoListActivity.getActivity(), String.format("%s %s", prefix, item.getFileName()), count, total);
            count++;

            int width = (int) (120 * mDensity);
            int height = (int) (80 * mDensity);

            // Get the thumbnail.
            Bitmap thumbnail = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            //Log.i(TAG, "create new bitmap for: " + item.getName());
            byte[] b = mLibVlc.getThumbnail(item.getLocation(), width, height);

            // Activity stopped & destroyed, abort everything
            if (isInterrupted())
                break;

            if (b == null) {// We were not able to create a thumbnail for this item.
                item.setPicture(mVideoListActivity.getActivity(), null);
                continue;
            }

            thumbnail.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            thumbnail = Util.cropBorders(thumbnail, width, height);

            Log.i(TAG, "Thumbnail created!");

            item.setPicture(mVideoListActivity.getActivity(), thumbnail);
            // Post to the file browser the new item.
            mVideoListActivity.setItemToUpdate(item);

            // Wait for the file browser to process the change.
            try {
                mVideoListActivity.await();
            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                break;
            }
        }
    }
}
