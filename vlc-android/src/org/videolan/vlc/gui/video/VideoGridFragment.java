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

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.videolan.android.ui.SherlockGridFragment;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaGroup;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.Util;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.interfaces.ISortable;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

public class VideoGridFragment extends SherlockGridFragment implements ISortable {

    public final static String TAG = "VLC/VideoListFragment";

    protected static final String ACTION_SCAN_START = "org.videolan.vlc.gui.ScanStart";
    protected static final String ACTION_SCAN_STOP = "org.videolan.vlc.gui.ScanStop";
    protected static final int UPDATE_ITEM = 0;

    /* Constants used to switch from Grid to List and vice versa */
    //FIXME If you know a way to do this in pure XML please do it!
    private static final int GRID_ITEM_WIDTH_DP = 156;
    private static final int GRID_HORIZONTAL_SPACING_DP = 20;
    private static final int GRID_VERTICAL_SPACING_DP = 20;
    private static final int GRID_STRETCH_MODE = GridView.STRETCH_COLUMN_WIDTH;
    private static final int LIST_HORIZONTAL_SPACING_DP = 0;
    private static final int LIST_VERTICAL_SPACING_DP = 10;
    private static final int LIST_STRETCH_MODE = GridView.STRETCH_COLUMN_WIDTH;

    protected LinearLayout mLayoutFlipperLoading;
    protected GridView mGridView;
    protected TextView mTextViewNomedia;
    protected Media mItemToUpdate;
    protected String mGroup;
    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);

    private VideoListAdapter mVideoAdapter;
    private MediaLibrary mMediaLibrary;
    private Thumbnailer mThumbnailer;
    private VideoGridAnimator mAnimator;

    private AudioServiceController mAudioController;

    // Gridview position saved in onPause()
    private int mGVFirstVisiblePos;

    /* All subclasses of Fragment must include a public empty constructor. */
    public VideoGridFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mVideoAdapter = new VideoListAdapter(getActivity(), this);
        mMediaLibrary = MediaLibrary.getInstance(getActivity());
        setListAdapter(mVideoAdapter);

        /* Load the thumbnailer */
        FragmentActivity activity = getActivity();
        if (activity != null)
            mThumbnailer = new Thumbnailer(activity, activity.getWindowManager().getDefaultDisplay());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getSherlockActivity().getSupportActionBar().setTitle(R.string.video);

        View v = inflater.inflate(R.layout.video_grid, container, false);

        // init the information for the scan (1/2)
        mLayoutFlipperLoading = (LinearLayout) v.findViewById(R.id.layout_flipper_loading);
        mTextViewNomedia = (TextView) v.findViewById(R.id.textview_nomedia);
        mGridView = (GridView) v.findViewById(android.R.id.list);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(getGridView());

        // init the information for the scan (2/2)
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SCAN_START);
        filter.addAction(ACTION_SCAN_STOP);
        getActivity().registerReceiver(messageReceiverVideoListFragment, filter);
        Log.i(TAG,"mMediaLibrary.isWorking() " + Boolean.toString(mMediaLibrary.isWorking()));
        if (mMediaLibrary.isWorking()) {
            actionScanStart(getActivity().getApplicationContext());
        }

        mAnimator = new VideoGridAnimator(getGridView());
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
        //Get & set times
        HashMap<String, Long> times = MediaDatabase.getInstance(getActivity()).getVideoTimes(getActivity());
        mVideoAdapter.setTimes(times);
        mVideoAdapter.notifyDataSetChanged();
        updateList();
        mMediaLibrary.addUpdateHandler(mHandler);
        mGridView.setSelection(mGVFirstVisiblePos);
        updateViewMode();
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

    private boolean hasSpaceForGrid(View v) {
        final Activity activity = getActivity();
        if (activity == null)
            return true;

        DisplayMetrics outMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(outMetrics);

        final int itemWidth = Util.convertDpToPx(GRID_ITEM_WIDTH_DP);
        final int horizontalspacing = Util.convertDpToPx(GRID_HORIZONTAL_SPACING_DP);
        final int width = mGridView.getPaddingLeft() + mGridView.getPaddingRight()
                + horizontalspacing + (itemWidth * 2);
        if (width < outMetrics.widthPixels)
            return true;
        return false;
    }

    private void updateViewMode() {
        if (getView() == null || getActivity() == null) {
            Log.w(TAG, "Unable to setup the view");
            return;
        }

        // Compute the left/right padding dynamically
        DisplayMetrics outMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        int sidePadding = (int) (outMetrics.widthPixels / 100 * Math.pow(outMetrics.density, 3) / 2);
        sidePadding = Math.max(0, Math.min(100, sidePadding));
        mGridView.setPadding(sidePadding, mGridView.getPaddingTop(),
                sidePadding, mGridView.getPaddingBottom());

        // Select between grid or list
        if (hasSpaceForGrid(getView())) {
            Log.d(TAG, "Switching to grid mode");
            mGridView.setNumColumns(GridView.AUTO_FIT);
            mGridView.setStretchMode(GRID_STRETCH_MODE);
            mGridView.setHorizontalSpacing(Util.convertDpToPx(GRID_HORIZONTAL_SPACING_DP));
            mGridView.setVerticalSpacing(Util.convertDpToPx(GRID_VERTICAL_SPACING_DP));
            mGridView.setColumnWidth(Util.convertDpToPx(GRID_ITEM_WIDTH_DP));
            mVideoAdapter.setListMode(false);
        } else {
            Log.d(TAG, "Switching to list mode");
            mGridView.setNumColumns(1);
            mGridView.setStretchMode(LIST_STRETCH_MODE);
            mGridView.setHorizontalSpacing(LIST_HORIZONTAL_SPACING_DP);
            mGridView.setVerticalSpacing(Util.convertDpToPx(LIST_VERTICAL_SPACING_DP));
            mVideoAdapter.setListMode(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            updateViewMode();
        }
    }

    @Override
    public void onGridItemClick(GridView l, View v, int position, long id) {
        Media media = (Media) getListAdapter().getItem(position);
        if (media instanceof MediaGroup) {
            MainActivity activity = (MainActivity)getActivity();
            VideoGridFragment frag = (VideoGridFragment)activity.showSecondaryFragment("videoGroupList");
            if (frag != null) {
                frag.setGroup(media.getTitle());
            }
        }
        else
            playVideo(media, false);
        super.onGridItemClick(l, v, position, id);
    }

    protected void playVideo(Media media, boolean fromStart) {
        VideoPlayerActivity.start(getActivity(), media.getLocation(), fromStart);
    }

    protected void playAudio(Media media) {
        mAudioController.load(media.getLocation(), true);
    }

    private boolean handleContextItemSelected(MenuItem menu, int position) {
        Media media = mVideoAdapter.getItem(position);
        switch (menu.getItemId())
        {
        case R.id.video_list_play_from_start:
            playVideo(media, true);
            return true;
        case R.id.video_list_play_audio:
            playAudio(media);
            return true;
        case R.id.video_list_info:
            MainActivity activity = (MainActivity)getActivity();
            MediaInfoFragment frag = (MediaInfoFragment)activity.showSecondaryFragment("mediaInfo");
            if (frag != null) {
                frag.setMediaLocation(media.getLocation());
            }
            return true;
        case R.id.video_list_delete:
            AlertDialog alertDialog = CommonDialogs.deleteMedia(
                    getActivity(),
                    media.getLocation(),
                    new VlcRunnable(media) {
                        @Override
                        public void run(Object o) {
                            Media media = (Media) o;
                            mMediaLibrary.getMediaItems().remove(media);
                            mVideoAdapter.remove(media);
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
        Media media = mVideoAdapter.getItem(info.position);
        if (media instanceof MediaGroup)
            return;
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.video_list, menu);
        setContextMenuItems(menu, media);
    }

    private void setContextMenuItems(Menu menu, Media media) {
        long lastTime = media.getTime();
        if (lastTime > 0) {
            MenuItem playFromStart = menu.findItem(R.id.video_list_play_from_start);
            playFromStart.setVisible(true);
        }
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
        Media media = mVideoAdapter.getItem(position);
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

    private static class VideoListHandler extends WeakHandler<VideoGridFragment> {
        public VideoListHandler(VideoGridFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoGridFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
            case UPDATE_ITEM:
                fragment.updateItem();
                break;
            case MediaLibrary.MEDIA_ITEMS_UPDATED:
                // Don't update the adapter while the layout animation is running
                if (fragment.mAnimator.isAnimationDone())
                    fragment.updateList();
                else
                    sendEmptyMessageDelayed(msg.what, 500);
                break;
            }
        }
    };

    private void updateItem() {
        mVideoAdapter.update(mItemToUpdate);
        try {
            mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {
        }
    }

    private void updateList() {
        List<Media> itemList = mMediaLibrary.getVideoItems();

        if (mThumbnailer != null)
            mThumbnailer.clearJobs();
        else
            Log.w(TAG, "Can't generate thumbnails, the thumbnailer is missing");

        mVideoAdapter.clear();

        if (itemList.size() > 0) {
            if (mGroup != null || itemList.size() <= 10) {
                for (Media item : itemList) {
                    if (mGroup == null || item.getTitle().startsWith(mGroup)) {
                        mVideoAdapter.add(item);
                        if (mThumbnailer != null)
                            mThumbnailer.addJob(item);
                    }
                }
            }
            else {
                List<MediaGroup> groups = MediaGroup.group(itemList);
                for (MediaGroup item : groups) {
                    mVideoAdapter.add(item.getMedia());
                    if (mThumbnailer != null)
                        mThumbnailer.addJob(item);
                }
            }
            mVideoAdapter.sort();
        }
    }

    @Override
    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

    public void setItemToUpdate(Media item) {
        mItemToUpdate = item;
        mHandler.sendEmptyMessage(UPDATE_ITEM);
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

            if (action.equalsIgnoreCase(ACTION_SCAN_START)) {
                mLayoutFlipperLoading.setVisibility(View.VISIBLE);
                mTextViewNomedia.setVisibility(View.INVISIBLE);
            } else if (action.equalsIgnoreCase(ACTION_SCAN_STOP)) {
                mLayoutFlipperLoading.setVisibility(View.INVISIBLE);
                mTextViewNomedia.setVisibility(View.VISIBLE);
            }
        }
    };

    public static void actionScanStart(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_START);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void actionScanStop(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_STOP);
        context.getApplicationContext().sendBroadcast(intent);
    }
}
