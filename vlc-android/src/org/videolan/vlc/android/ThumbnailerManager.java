package org.videolan.vlc.android;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.videolan.vlc.android.SimpleFileBrowser.FileBrowserItem;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

public class ThumbnailerManager extends Thread {
    
    private final LinkedList<Integer> mIds = new LinkedList<Integer>();
    
    final Lock lock = new ReentrantLock();
    final Condition notEmpty = lock.newCondition();
    
    final private LibVLC mLibVlc = LibVLC.getInstance();
    
    SimpleFileBrowser mFileBrowser;
    
    public ThumbnailerManager(SimpleFileBrowser fileBrowser) {
        mFileBrowser = fileBrowser; 
        start();
    }
    
    /**
     * Remove all the thumbnail jobs.
     */
    public void clearJobs()
    {
        lock.lock();
        mIds.clear();
        lock.unlock();
    }
    
    /**
     * Add a new id of the file browser item to create its thumbnail.
     * @param id the if of the file browser item.
     */
    public void addJob(int id) {
        lock.lock();
        mIds.add(id);
        notEmpty.signal();
        lock.unlock();
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
            while (mIds.size() == 0)
            {
                try {
                    notEmpty.await();
                } catch (InterruptedException e) {
                    killed = true;
                    break;
                }
            }
            if (killed)
                break;

            int id = mIds.getFirst();
            mIds.removeFirst();
            lock.unlock();

            FileBrowserItem oldItem = mFileBrowser.mItems.getItem(id);

            // Get the thumbnail.
            Bitmap thumbnail = Bitmap.createBitmap(50, 50, Config.ARGB_8888);
            thumbnail.copyPixelsFromBuffer(
                    ByteBuffer.wrap(mLibVlc.getThumbnail(oldItem.path, 50, 50)));

            FileBrowserItem newItem = mFileBrowser.new FileBrowserItem(oldItem.name,
                    oldItem.path, thumbnail, oldItem.count);

            mFileBrowser.mItemIdToUpdate = id;
            mFileBrowser.mNewItem = newItem;
            
            // Post to the file browser the new item.
            mFileBrowser.mHandler.post(mFileBrowser.mUpdateItems);
            
            // Wait for the file browser to process the change.
            try {
                mFileBrowser.mBarrier.await();
            } catch (InterruptedException e) {
                break;
            } catch (BrokenBarrierException e) {
                break;
            }
        }
    }
}
