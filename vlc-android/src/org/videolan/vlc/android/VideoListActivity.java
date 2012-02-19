/*****************************************************************************
 * VideoListActivity.java
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

package org.videolan.vlc.android;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

public class VideoListActivity extends ListActivity implements Sortable {

    private LinearLayout mNoFileLayout;
    private LinearLayout mLoadFileLayout;
    private VideoListAdapter mVideoAdapter;

    protected Media mItemToUpdate;

    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    protected ThumbnailerManager mThumbnailerManager;

    protected static final int UPDATE_ITEM = 0;

    private MediaLibrary mMediaLibrary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_list);

        mVideoAdapter = new VideoListAdapter(this, R.layout.video_list_item);
        mNoFileLayout = (LinearLayout) findViewById(R.id.video_list_empty_nofile);
        mLoadFileLayout = (LinearLayout) findViewById(R.id.video_list_empty_loadfile);

        mMediaLibrary = MediaLibrary.getInstance();
        mMediaLibrary.addUpdateHandler(mHandler);
        mThumbnailerManager = new ThumbnailerManager(this);

        setListAdapter(mVideoAdapter);
    }

    @Override
    protected void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mThumbnailerManager.clearJobs();
        mVideoAdapter.clear();
        super.onDestroy();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Stop the currently running audio
        AudioServiceController asc = AudioServiceController.getInstance();
        asc.stop();

        Media item = (Media) getListAdapter().getItem(position);
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("filePath", item.getPath());
        startActivity(intent);
        super.onListItemClick(l, v, position, id);
    }

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivity(intent);
        return false;
    }

    /**
     * Handle changes on the list
     */
    protected Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_ITEM:
                mVideoAdapter.update(mItemToUpdate);
                try {
                    mBarrier.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
                break;
            case MediaLibrary.MEDIA_ITEMS_UPDATED:
                updateList();
                break;
        }
    }
    };

    private void updateList() {

        MainActivity mainActivity = MainActivity.getInstance();

        List<Media> itemList = mMediaLibrary.getVideoItems();

        mVideoAdapter.clear();

        if (itemList.size() > 0) {
            for (Media item : itemList) {
                if (item.getType() == Media.TYPE_VIDEO) {
                    mVideoAdapter.add(item);
                    if (item.getPicture() == null)
                        mThumbnailerManager.addJob(item);
                }
            }
            mVideoAdapter.sort();
        } else {
            mLoadFileLayout.setVisibility(View.INVISIBLE);
            mNoFileLayout.setVisibility(View.VISIBLE);
        }

        mainActivity.mHandler.sendEmptyMessage(MainActivity.HIDE_PROGRESSBAR);

    }

    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

}
