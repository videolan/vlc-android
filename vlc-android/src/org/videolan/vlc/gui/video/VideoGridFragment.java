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
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaGroup;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.BrowserFragment;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCRunnable;
import org.videolan.vlc.widget.SwipeRefreshLayout;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class VideoGridFragment extends BrowserFragment implements ISortable, IVideoBrowser, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    public final static String TAG = "VLC/VideoListFragment";

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

    private AudioServiceController mAudioController;
    private boolean mReady = true;

    // Gridview position saved in onPause()
    private int mGVFirstVisiblePos;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mVideoAdapter = new VideoListAdapter(getActivity(), this);
        mMediaLibrary = MediaLibrary.getInstance();

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
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

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
        Log.i(TAG, "mMediaLibrary.isWorking() " + Boolean.toString(mMediaLibrary.isWorking()));
        if (mMediaLibrary.isWorking()) {
            Util.actionScanStart();
        }

        mAnimator = new VideoGridAnimator(mGridView);
    }

    @Override
    public void onPause() {
        super.onPause();
        mGVFirstVisiblePos = mGridView.getFirstVisiblePosition();
        mMediaLibrary.removeUpdateHandler(mHandler);

        /* Stop the thumbnailer */
        if (mThumbnailer != null)
            mThumbnailer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMediaLibrary.addUpdateHandler(mHandler);
        final boolean refresh = mVideoAdapter.isEmpty();
        if (refresh)
            updateList();
        else {
            mViewNomedia.setVisibility(View.GONE);
            focusHelper(false);
        }
        //Get & set times
        HashMap<String, Long> times = MediaDatabase.getInstance().getVideoTimes(getActivity());
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
            activity.showSecondaryFragment("videoGroupList", media.getTitle());
        }
        else
            playVideo(media, false);
    }

    protected void playVideo(MediaWrapper media, boolean fromStart) {
        VideoPlayerActivity.start(getActivity(), media.getLocation(), fromStart);
    }

    protected void playAudio(MediaWrapper media) {
        mAudioController.load(media.getLocation(), true);
    }

    private boolean handleContextItemSelected(MenuItem menu, int position) {
        MediaWrapper media = mVideoAdapter.getItem(position);
        if (media == null)
            return false;
        switch (menu.getItemId())
        {
        case R.id.video_list_play_from_start:
            playVideo(media, true);
            return true;
        case R.id.video_list_play_audio:
            playAudio(media);
            return true;
        case R.id.video_list_info:
            Activity activity = getActivity();
            if (activity instanceof MainActivity)
                ((MainActivity)activity).showSecondaryFragment("mediaInfo", media.getLocation());
            else {
                Intent i = new Intent(activity, SecondaryActivity.class);
                i.putExtra("fragment", "mediaInfo");
                i.putExtra("param", media.getLocation());
                startActivity(i);
            }
            return true;
        case R.id.video_list_delete:
            AlertDialog alertDialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    media.getLocation(),
                    new VLCRunnable(media) {
                        @Override
                        public void run(Object o) {
                            MediaWrapper media = (MediaWrapper) o;
                            mMediaLibrary.getMediaItems().remove(media);
                            mVideoAdapter.remove(media);
                            if (mAudioController.getMediaLocations().contains(media.getLocation()))
                                mAudioController.removeLocation(media.getLocation());
                        }
                    });
            alertDialog.show();
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
        final Media media = new Media(VLCInstance.get(), mediaWrapper.getLocation());
        media.parse();
        media.release();
        if (media.getMeta(Media.Meta.Title) != null)
            hasInfo = true;
        menu.findItem(R.id.video_list_info).setVisible(hasInfo);
        menu.findItem(R.id.video_list_delete).setVisible(!LibVlcUtil.isLolliPopOrLater() ||
                mediaWrapper.getLocation().startsWith("file://"+ Environment.getExternalStorageDirectory().getPath()));
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
        if (!LibVlcUtil.isHoneycombOrLater()) {
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
                    if (mReady)
                        display();
                }
            }).start();
        } else
            focusHelper(true);
        stopRefresh();
    }

    @Override
    public void showProgressBar() {
        if (getActivity() instanceof MainActivity)
            MainActivity.showProgressBar();
    }

    @Override
    public void hideProgressBar() {
        if (getActivity() instanceof MainActivity)
            MainActivity.hideProgressBar();
    }

    @Override
    public void clearTextInfo() {
        if (getActivity() instanceof MainActivity)
            MainActivity.clearTextInfo();
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        if (getActivity() instanceof MainActivity)
            MainActivity.sendTextInfo(info, progress, max);
    }

    @Override
    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
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
            MediaLibrary.getInstance().loadMediaItems(getActivity(), true);
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReady)
            display();
        else
            mReady = ready;
    }

    @Override
    public void display() {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mViewNomedia.setVisibility(mVideoAdapter.getCount()>0 ? View.GONE : View.VISIBLE);
                    mReady = true;
                    mVideoAdapter.sort();
                    mVideoAdapter.notifyDataSetChanged();
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
}
