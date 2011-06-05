package org.videolan.vlc.android;


import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.Log;

public class ThumbnailerManager extends Thread {
	public final static String TAG = "VLC/ThumbnailerManager";
    
    private final Queue<MediaItem> mItems = new LinkedList<MediaItem>();
    
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    
    private LibVLC mLibVlc;  
    private MediaLibraryActivity mMediaLibraryActivity;

    
    public ThumbnailerManager() {
    	mMediaLibraryActivity = MediaLibraryActivity.getInstance();
    	try {
			mLibVlc = LibVLC.getInstance();
		} catch (LibVlcException e) {
			e.printStackTrace();
		} 
        start();
    }
    
    
    /**
     * Remove all the thumbnail jobs.
     */
    public void clearJobs()
    {
        lock.lock();
        mItems.clear();
        lock.unlock();
    }
    
    /**
     * Add a new id of the file browser item to create its thumbnail.
     * @param id the if of the file browser item.
     */
    public void addJob(MediaItem item) {
        lock.lock();
        mItems.add(item);
        notEmpty.signal();
        lock.unlock();
        Log.i(TAG, "Job added!");
    }
    
    /**
     * Thread main function.
     */
    public void run()
    {
        while (!isInterrupted()) {
            lock.lock();
            // Get the id of the file browser item to create its thumbnail.
            boolean killed = false;
            while (mItems.size() == 0)
            {
                try {
                	mMediaLibraryActivity.mHandler.post(
                			mMediaLibraryActivity.mHideProgressBar);
                	Log.i(TAG, "hide ProgressBar!");
                    notEmpty.await();
                } catch (InterruptedException e) {
                    killed = true;
                    break;
                }
            }
            if (killed)
                break;
            lock.unlock();
            
            MediaItem item = mItems.poll();
            mMediaLibraryActivity.mHandler.post(
        			mMediaLibraryActivity.mShowProgressBar);   
            
            Log.i(TAG, "show ProgressBar!");
            
            // Get the thumbnail.
            Bitmap thumbnail = Bitmap.createBitmap(120, 72, Config.ARGB_8888);
            Log.i(TAG, "create new bitmap for: " + item.getName());
            byte[] b = mLibVlc.getThumbnail(item.getPath(), 120, 72);
            Log.i(TAG, "lib bla!");

            if (b == null) // We were not able to create a thumbnail for this item.
                continue;

            thumbnail.copyPixelsFromBuffer(ByteBuffer.wrap(b));

            Log.i(TAG, "Thumbnail created!");

            item.setThumbnail(thumbnail);
            mMediaLibraryActivity.mItemToUpdate = item;     
            // Post to the file browser the new item.
            mMediaLibraryActivity.mHandler.post(
            		mMediaLibraryActivity.mUpdateMediaItem);

            
            // Wait for the file browser to process the change.
            try {
            	mMediaLibraryActivity.mBarrierItem.await();
            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                break;
            }
        }
    }
    
    
}
