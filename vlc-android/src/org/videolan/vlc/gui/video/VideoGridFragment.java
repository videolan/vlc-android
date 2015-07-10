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

package org.videolan.vlc.gui.video;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaGroup;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class VideoGridFragment extends MediaBrowserFragment implements ISortable, IVideoBrowser, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    public final static String TAG = "VLC/VideoListFragment";

    public final static String KEY_GROUP = "key_group";

    private static final int DELETE_MEDIA = 0;
    private static final int DELETE_DURATION = 3000;

    protected static final String ACTION_SCAN_START = "org.videolan.vlc.gui.ScanStart";
    protected static final String ACTION_SCAN_STOP = "org.videolan.vlc.gui.ScanStop";
    protected static final int UPDATE_ITEM = 0;

    /* Constants used to switch from Grid to List and vice versa */
    //FIXME If you know a way to do this in pure XML please do it!
    private static final int GRID_STRETCH_MODE = GridView.STRETCH_COLUMN_WIDTH;
    private static final int LIST_STRETCH_MODE = GridView.STRETCH_COLUMN_WIDTH;

    protected LinearLayout mLayoutFlipperLoading;
    protected GridView mGridView;
    protected TextView mTextViewNomedia;
    protected View mViewNomedia;
    protected MediaWrapper mItemToUpdate;
    protected String mGroup;
    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);

    private VideoListAdapter mVideoAdapter;
    private MediaLibrary mMediaLibrary;
    private Thumbnailer mThumbnailer;
    private VideoGridAnimator mAnimator;

    private MainActivity mMainActivity;

    // Gridview position saved in onPause()
    private int mGVFirstVisiblePos;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVideoAdapter = new VideoListAdapter(this);
        mMediaLibrary = MediaLibrary.getInstance();

        if (savedInstanceState != null)
            setGroup(savedInstanceState.getString(KEY_GROUP));
        /* Load the thumbnailer */
        FragmentActivity activity = getActivity();
        if (activity != null)
            mThumbnailer = new Thumbnailer(activity, activity.getWindowManager().getDefaultDisplay());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.video_grid, container, false);

        // init the information for the scan (1/2)
        mLayoutFlipperLoading = (LinearLayout) v.findViewById(R.id.layout_flipper_loading);
        mTextViewNomedia = (TextView) v.findViewById(R.id.textview_nomedia);
        mViewNomedia = v.findViewById(android.R.id.empty);
        mGridView = (GridView) v.findViewById(android.R.id.list);
        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mSwipeRefreshLayout.setEnabled(firstVisibleItem == 0);
            }
        });
        mGridView.setAdapter(mVideoAdapter);
        mGridView.setOnItemClickListener(this);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(mGridView);

        // init the information for the scan (2/2)
        IntentFilter filter = new IntentFilter();
        filter.addAction(Util.ACTION_SCAN_START);
        filter.addAction(Util.ACTION_SCAN_STOP);
        getActivity().registerReceiver(messageReceiverVideoListFragment, filter);
        if (mMediaLibrary.isWorking()) {
            Util.actionScanStart();
        }

        mAnimator = new VideoGridAnimator(mGridView);
    }

    @Override
    public void onPause() {
        super.onPause();
        mGVFirstVisiblePos = mGridView.getFirstVisiblePosition();
        mMediaLibrary.setBrowser(null);
        mMediaLibrary.removeUpdateHandler(mHandler);

        /* Stop the thumbnailer */
        if (mThumbnailer != null)
            mThumbnailer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((getActivity() instanceof MainActivity))
            mMainActivity = (MainActivity) getActivity();
        mMediaLibrary.setBrowser(this);
        mMediaLibrary.addUpdateHandler(mHandler);
        final boolean refresh = mVideoAdapter.isEmpty();
        if (refresh)
            updateList();
        else {
            mViewNomedia.setVisibility(View.GONE);
            focusHelper(false);
        }
        //Get & set times
        HashMap<String, Long> times = MediaDatabase.getInstance().getVideoTimes();
        mVideoAdapter.setTimes(times);
        mGridView.setSelection(mGVFirstVisiblePos);
        updateViewMode();
        if (mGroup == null && refresh)
            mAnimator.animate();

        /* Start the thumbnailer */
        if (mThumbnailer != null)
            mThumbnailer.start(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_GROUP, mGroup);
    }

    @Override
    public void onDestroyView() {
        getActivity().unregisterReceiver(messageReceiverVideoListFragment);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mThumbnailer != null)
            mThumbnailer.clearJobs();
        mBarrier.reset();
        mVideoAdapter.clear();
    }

    protected String getTitle(){
        if (mGroup == null)
            return getString(R.string.video);
        else
            return mGroup;
    }

    private void updateViewMode() {
        if (getView() == null || getActivity() == null) {
            Log.w(TAG, "Unable to setup the view");
            return;
        }
        Resources res = getResources();
        boolean listMode = res.getBoolean(R.bool.list_mode);
        listMode |= res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT &&
                PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("force_list_portrait", false);
        // Compute the left/right padding dynamically
        DisplayMetrics outMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        int sidePadding;

        // Select between grid or list
        if (!listMode) {
            sidePadding = (int) ((float)outMetrics.widthPixels / 100f * (float)Math.pow(outMetrics.density, 3) / 2f);
            mGridView.setNumColumns(GridView.AUTO_FIT);
            mGridView.setStretchMode(GRID_STRETCH_MODE);
            mGridView.setColumnWidth(res.getDimensionPixelSize(R.dimen.grid_card_width));
            mGridView.setVerticalSpacing(res.getDimensionPixelSize(R.dimen.grid_card_vertical_spacing));
            mVideoAdapter.setListMode(false);
        } else {
            sidePadding = res.getDimensionPixelSize(R.dimen.listview_side_padding);
            mGridView.setNumColumns(1);
            mGridView.setStretchMode(LIST_STRETCH_MODE);
            mGridView.setVerticalSpacing(0);
            mGridView.setHorizontalSpacing(0);
            mVideoAdapter.setListMode(true);
        }
        sidePadding = Math.max(0, Math.min(100, sidePadding));
        mGridView.setPadding(sidePadding, mGridView.getPaddingTop(),
                sidePadding, mGridView.getPaddingBottom());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MediaWrapper media = mVideoAdapter.getItem(position);
        if (media == null)
            return;
        if (media instanceof MediaGroup) {
            MainActivity activity = (MainActivity)getActivity();
            activity.showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, media.getTitle());
        }
        else
            playVideo(media, false);
    }

    protected void playVideo(MediaWrapper media, boolean fromStart) {
        media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
        VideoPlayerActivity.start(getActivity(), media.getUri(), fromStart);
    }

    protected void playAudio(MediaWrapper media) {
        if (mService != null) {
            media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            mService.load(media);
        }
    }

    private boolean handleContextItemSelected(MenuItem menu, int position) {
        if (position >= mVideoAdapter.getCount())
            return false;
        MediaWrapper media = mVideoAdapter.getItem(position);
        if (media == null)
            return false;
        switch (menu.getItemId()){
            case R.id.video_list_play_from_start:
                playVideo(media, true);
                return true;
            case R.id.video_list_play_audio:
                playAudio(media);
                return true;
            case R.id.video_list_info:
                Activity activity = getActivity();
                if (activity instanceof MainActivity)
                    ((MainActivity)activity).showSecondaryFragment(SecondaryActivity.MEDIA_INFO, media.getLocation());
                else {
                    Intent i = new Intent(activity, SecondaryActivity.class);
                    i.putExtra("fragment", "mediaInfo");
                    i.putExtra("param", media.getLocation());
                    startActivity(i);
                }
                return true;
            case R.id.video_list_delete:
                Snackbar.make(getView(), getString(R.string.file_deleted), Snackbar.LENGTH_LONG)
                    .setAction(android.R.string.cancel, mCancelDeleteMediaListener)
                    .show();
                Message msg = mDeleteHandler.obtainMessage(DELETE_MEDIA, position, 0);
                mDeleteHandler.sendMessageDelayed(msg, DELETE_DURATION);
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // Do not show the menu of media group.
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        MediaWrapper media = mVideoAdapter.getItem(info.position);
        if (media == null || media instanceof MediaGroup)
            return;
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.video_list, menu);
        setContextMenuItems(menu, media);
    }

    private void setContextMenuItems(Menu menu, MediaWrapper mediaWrapper) {
        long lastTime = mediaWrapper.getTime();
        if (lastTime > 0)
            menu.findItem(R.id.video_list_play_from_start).setVisible(true);

        boolean hasInfo = false;
        final Media media = new Media(VLCInstance.get(), mediaWrapper.getUri());
        media.parse();
        if (media.getMeta(Media.Meta.Title) != null)
            hasInfo = true;
        media.release();
        menu.findItem(R.id.video_list_info).setVisible(hasInfo);
        menu.findItem(R.id.video_list_delete).setVisible(!AndroidUtil.isLolliPopOrLater() ||
                mediaWrapper.getLocation().startsWith("file://" + AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY));
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menu.getMenuInfo();
        if (info != null && handleContextItemSelected(menu, info.position))
            return true;
        return super.onContextItemSelected(menu);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onContextPopupMenu(View anchor, final int position) {
        if (!AndroidUtil.isHoneycombOrLater()) {
            // Call the "classic" context menu
            anchor.performLongClick();
            return;
        }

        PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
        popupMenu.getMenuInflater().inflate(R.menu.video_list, popupMenu.getMenu());
        MediaWrapper media = mVideoAdapter.getItem(position);
        if (media == null)
            return;
        setContextMenuItems(popupMenu.getMenu(), media);
        popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleContextItemSelected(item, position);
            }
        });
        popupMenu.show();
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new VideoListHandler(this);

    public void updateItem() {
        mVideoAdapter.update(mItemToUpdate);
        try {
            mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {
        }
    }
    private void focusHelper(boolean idIsEmpty) {
        View parent = getView();
        if (getActivity() == null || !(getActivity() instanceof MainActivity))
            return;
        MainActivity activity = (MainActivity)getActivity();
        activity.setMenuFocusDown(idIsEmpty, android.R.id.list);
        activity.setSearchAsFocusDown(idIsEmpty, parent,
                android.R.id.list);
        }

    public void updateList() {
        if (!mSwipeRefreshLayout.isRefreshing())
            mSwipeRefreshLayout.setRefreshing(true);
        final List<MediaWrapper> itemList = mMediaLibrary.getVideoItems();

        if (mThumbnailer != null)
            mThumbnailer.clearJobs();
        else
            Log.w(TAG, "Can't generate thumbnails, the thumbnailer is missing");

        mVideoAdapter.setNotifyOnChange(true);
        mVideoAdapter.clear();

        if (itemList.size() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mGroup != null || itemList.size() <= 10) {
                        for (MediaWrapper item : itemList) {
                            if (mGroup == null || item.getTitle().startsWith(mGroup)) {
                                mVideoAdapter.setNotifyOnChange(false);
                                mVideoAdapter.add(item);
                                if (mThumbnailer != null)
                                    mThumbnailer.addJob(item);
                            }
                        }
                    }
                    else {
                        List<MediaGroup> groups = MediaGroup.group(itemList);
                        for (MediaGroup item : groups) {
                            mVideoAdapter.setNotifyOnChange(false);
                            mVideoAdapter.add(item.getMedia());
                            if (mThumbnailer != null)
                                mThumbnailer.addJob(item);
                        }
                    }
                    if (mReadyToDisplay)
                        display();
                }
            }).start();
        } else
            focusHelper(true);
        stopRefresh();
    }

    @Override
    public void showProgressBar() {
        if (mMainActivity != null)
            mMainActivity.showProgressBar();
    }

    @Override
    public void hideProgressBar() {
        if (mMainActivity != null)
            mMainActivity.hideProgressBar();
    }

    @Override
    public void clearTextInfo() {
        if (mMainActivity != null)
            mMainActivity.clearTextInfo();
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        if (mMainActivity != null)
            mMainActivity.sendTextInfo(info, progress, max);
    }

    @Override
    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

    @Override
    public int sortDirection(int sortby) {
        return mVideoAdapter.sortDirection(sortby);
    }

    public void setItemToUpdate(MediaWrapper item) {
        mItemToUpdate = item;
        mHandler.sendEmptyMessage(VideoListHandler.UPDATE_ITEM);
    }

    public void setGroup(String prefix) {
        mGroup = prefix;
    }

    public void await() throws InterruptedException, BrokenBarrierException {
        mBarrier.await();
    }

    public void resetBarrier() {
        mBarrier.reset();
    }

    private final BroadcastReceiver messageReceiverVideoListFragment = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Util.ACTION_SCAN_START)) {
                mLayoutFlipperLoading.setVisibility(View.VISIBLE);
                mTextViewNomedia.setVisibility(View.INVISIBLE);
            } else if (action.equalsIgnoreCase(Util.ACTION_SCAN_STOP)) {
                mLayoutFlipperLoading.setVisibility(View.INVISIBLE);
                mTextViewNomedia.setVisibility(View.VISIBLE);
            }
        }
    };

    public void stopRefresh() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        if (getActivity()!=null && !MediaLibrary.getInstance().isWorking())
            MediaLibrary.getInstance().loadMediaItems(true);
    }

    @Override
    public void display() {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mViewNomedia.setVisibility(mVideoAdapter.getCount() > 0 ? View.GONE : View.VISIBLE);
                    mReadyToDisplay = true;
                    mVideoAdapter.setNotifyOnChange(true);
                    mVideoAdapter.sort();
                    mGVFirstVisiblePos = mGridView.getFirstVisiblePosition();
                    mGridView.setSelection(mGVFirstVisiblePos);
                    mGridView.requestFocus();
                    focusHelper(false);
                }
            });
    }

    public void clear(){
        mVideoAdapter.clear();
    }

    public void deleteMedia(int position){
        final MediaWrapper media = mVideoAdapter.getItem(position);
        final String path = media.getUri().getPath();
        new Thread(new Runnable() {
            public void run() {
                Util.recursiveDelete(VLCApplication.getAppContext(), new File(path));
            }
        }).start();
        mMediaLibrary.getMediaItems().remove(media);
        mVideoAdapter.remove(media);
        if (mService != null) {
            final List<String> list = mService.getMediaLocations();
            if (list != null && list.contains(media.getLocation())) {
                mService.removeLocation(media.getLocation());
            }
        }
    }


    View.OnClickListener mCancelDeleteMediaListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mDeleteHandler.removeMessages(DELETE_MEDIA);
        }
    };

    Handler mDeleteHandler = new VideoDeleteHandler(this);

    private static class VideoDeleteHandler extends WeakHandler<VideoGridFragment>{

        public VideoDeleteHandler(VideoGridFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case DELETE_MEDIA:
                    getOwner().deleteMedia(msg.arg1);
            }
        }
    }
}
