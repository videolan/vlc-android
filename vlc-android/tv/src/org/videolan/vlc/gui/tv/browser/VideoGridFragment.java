/*
 * *************************************************************************
 *  VideoGridFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.tv.browser;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.video.VideoListHandler;
import org.videolan.vlc.interfaces.IVideoBrowser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;

public class VideoGridFragment extends MediaLibBrowserFragment implements IVideoBrowser {

    private Handler mHandler = new VideoListHandler(this);
    protected static Thumbnailer sThumbnailer;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sThumbnailer = MainTvActivity.getThumbnailer();
    }

    public void onResume() {
        super.onResume();
        if (mAdapter.size() == 0) {
            new AsyncVideoUpdate().execute();
        }
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(this);
    }

    public void onPause() {
        super.onPause();
        mMediaLibrary.removeUpdateHandler(mHandler);
        /* unregister from thumbnailer */
        if (sThumbnailer != null)
            sThumbnailer.setVideoBrowser(null);
    }

    public class AsyncVideoUpdate extends AsyncTask<Void, MediaWrapper, Void> {

        public AsyncVideoUpdate() {}

        @Override
        protected void onPreExecute(){
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
        }
        @Override
        protected Void doInBackground(Void... params) {
            int size;
            MediaWrapper MediaWrapper;

            ArrayList<MediaWrapper> mediaList = mMediaLibrary.getVideoItems();
            size = mediaList == null ? 0 : mediaList.size();
            mMediaIndex = new HashMap<String, Integer>(size);

            for (int i = 0 ; i < size ; ++i){
                MediaWrapper = mediaList.get(i);
                mMediaIndex.put(MediaWrapper.getLocation(), i);
                publishProgress(MediaWrapper);
            }
            return null;
        }

        protected void onProgressUpdate(MediaWrapper... medias){
            mAdapter.add(medias[0]);
        }

        @Override
        protected void onPostExecute(Void result) {
            setOnItemViewClickedListener(mClickListener);
        }
    }

    @Override
    public void setItemToUpdate(MediaWrapper item) {
        mItemToUpdate = item;
        mHandler.sendEmptyMessage(VideoListHandler.UPDATE_ITEM);
    }

    public void updateItem() {
        if (mAdapter != null && mMediaIndex != null && mItemToUpdate != null
                && mMediaIndex.containsKey(mItemToUpdate.getLocation()))
            mAdapter.notifyArrayItemRangeChanged(mMediaIndex.get(mItemToUpdate.getLocation()), 1);
        try {
            mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {}
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {}

    @Override
    public void updateList() {}

    @Override
    public void showProgressBar() {}

    @Override
    public void hideProgressBar() {}

    @Override
    public void clearTextInfo() {}

    public void await() throws InterruptedException, BrokenBarrierException {
        mBarrier.await();
    }

    public void resetBarrier() {
        mBarrier.reset();
    }
}
