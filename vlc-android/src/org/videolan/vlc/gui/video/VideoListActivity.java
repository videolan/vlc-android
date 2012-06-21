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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.ThumbnailerManager;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.interfaces.ISortable;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

public class VideoListActivity extends SherlockListFragment implements ISortable {

    private LinearLayout mNoFileLayout;
    private LinearLayout mLoadFileLayout;
    private VideoListAdapter mVideoAdapter;

    protected Media mItemToUpdate;

    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    protected ThumbnailerManager mThumbnailerManager;

    protected static final int UPDATE_ITEM = 0;

    private MediaLibrary mMediaLibrary;

    public final static int MENU_PLAY = Menu.FIRST;
    public final static int MENU_INFO = Menu.FIRST + 1;
    public final static int MENU_DELETE = Menu.FIRST + 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVideoAdapter = new VideoListAdapter(getActivity());

        mMediaLibrary = MediaLibrary.getInstance(getActivity());
        mMediaLibrary.addUpdateHandler(mHandler);
        mThumbnailerManager = new ThumbnailerManager(this);

        setListAdapter(mVideoAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.video_list, container, false);

        mNoFileLayout = (LinearLayout) v.findViewById(R.id.video_list_empty_nofile);
        mLoadFileLayout = (LinearLayout) v.findViewById(R.id.video_list_empty_loadfile);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnCreateContextMenuListener(contextMenuListener);
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    public void onResume() {
        //Get & highlight the last media
        SharedPreferences preferences = getActivity().getSharedPreferences(PreferencesActivity.NAME, Context.MODE_PRIVATE);
        String lastPath = preferences.getString("LastMedia", null);
        long lastTime = preferences.getLong("LastTime", 0);
        mVideoAdapter.setLastMedia(lastTime, lastPath);
        mVideoAdapter.notifyDataSetChanged();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mThumbnailerManager.clearJobs();
        mVideoAdapter.clear();
        super.onDestroy();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
    	playVideo(position);
        super.onListItemClick(l, v, position, id);
    }

    protected void playVideo(int position) {
        // Stop the currently running audio
        AudioServiceController asc = AudioServiceController.getInstance();
        asc.stop();

        Media item = (Media) getListAdapter().getItem(position);
        Intent intent = new Intent(getActivity(), VideoPlayerActivity.class);
        intent.putExtra("itemLocation", item.getLocation());
        startActivity(intent);
    }

	OnCreateContextMenuListener contextMenuListener = new OnCreateContextMenuListener()
    {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            menu.add(Menu.NONE, MENU_PLAY, Menu.NONE, R.string.play);
            menu.add(Menu.NONE, MENU_INFO, Menu.NONE, R.string.info);
            menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete);
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) menu.getMenuInfo();
    	switch (menu.getItemId())
        {
            case MENU_PLAY:
            	playVideo(info.position);
                break;
            case MENU_INFO:
                Intent intent = new Intent(getActivity(), MediaInfoActivity.class);
                intent.putExtra("itemLocation",
                		mVideoAdapter.getItem(info.position).getLocation());
                startActivity(intent);
                break;
            case MENU_DELETE:
                final int positionDelete = info.position;
                AlertDialog alertDialog = new AlertDialog.Builder(getActivity().getParent())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.validation)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        URI adressMediaUri = null;
                        try {
                            adressMediaUri = new URI (mVideoAdapter.
                            		getItem(positionDelete).getLocation());
                        } catch (URISyntaxException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        File fileMedia =  new File(adressMediaUri);
                        fileMedia.delete();
                        mVideoAdapter.remove(mVideoAdapter.getItem(positionDelete));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).create();
                alertDialog.show();
                break;
            default:
                return true;
        }
        return super.onContextItemSelected(menu);
    }

    /*@Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(getActivity(), SearchActivity.class);
        startActivity(intent);
        return false;
    }*/

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
    }

    public void sortBy(int sortby) {
        mVideoAdapter.sortBy(sortby);
    }

    public void setItemToUpdate(Media item) {
        mItemToUpdate = item;
        mHandler.sendEmptyMessage(UPDATE_ITEM);
    }

    public void await() throws InterruptedException, BrokenBarrierException {
        mBarrier.await();
    }

}
