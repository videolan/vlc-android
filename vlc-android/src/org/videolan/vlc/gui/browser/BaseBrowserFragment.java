/**
 * **************************************************************************
 * BaseBrowserFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MediaInfoDialog;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.LinkedList;


public abstract class BaseBrowserFragment extends MediaBrowserFragment implements IRefreshable, MediaBrowser.EventListener, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, Filterable {
    protected static final String TAG = "VLC/BaseBrowserFragment";

    public static String ROOT = "smb";
    public static final String KEY_MRL = "key_mrl";
    public static final String KEY_MEDIA = "key_media";
    public static final String KEY_MEDIA_LIST = "key_media_list";
    public static final String KEY_CONTENT_LIST = "key_content_list";
    public static final String KEY_POSITION = "key_list";

    protected FloatingActionButton mFAB;

    protected BrowserFragmentHandler mHandler;
    protected MediaBrowser mMediaBrowser;
    protected ContextMenuRecyclerView mRecyclerView;
    private View mSearchButtonView;
    protected BaseBrowserAdapter mAdapter;
    protected LinearLayoutManager mLayoutManager;
    protected TextView mEmptyView;
    public String mMrl;
    protected MediaWrapper mCurrentMedia;
    protected int mSavedPosition = -1, mFavorites = 0;
    public boolean mRoot;
    boolean goBack = false;

    private SparseArray<ArrayList<MediaWrapper>> mFoldersContentLists;
    private ArrayList<MediaWrapper> mediaList;
    public int mCurrentParsedPosition = 0;

    protected abstract Fragment createFragment();
    protected abstract void browseRoot();
    protected abstract String getCategoryTitle();

    public BaseBrowserFragment(){
        mHandler = new BrowserFragmentHandler(this);
        mAdapter = new BaseBrowserAdapter(this);
    }

    public void onCreate(Bundle bundle){
        super.onCreate(bundle);

        if (bundle == null)
            bundle = getArguments();
        else
            mFoldersContentLists = (SparseArray<ArrayList<MediaWrapper>>) VLCApplication.getData(KEY_CONTENT_LIST);
        if (mFoldersContentLists == null)
            mFoldersContentLists = new SparseArray<>();
        if (bundle != null){
            mediaList = (ArrayList<MediaWrapper>) VLCApplication.getData(KEY_MEDIA_LIST);
            if (mediaList != null)
                mAdapter.addAll(mediaList);
            mCurrentMedia = bundle.getParcelable(KEY_MEDIA);
            if (mCurrentMedia != null)
                mMrl = mCurrentMedia.getLocation();
            else
                mMrl = bundle.getString(KEY_MRL);
            mSavedPosition = bundle.getInt(KEY_POSITION);
        } else if (getActivity().getIntent() != null){
            mMrl = getActivity().getIntent().getDataString();
            getActivity().setIntent(null);
        }
    }

    protected int getLayoutId(){
        return R.layout.directory_browser;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(getLayoutId(), container, false);
        mRecyclerView = (ContextMenuRecyclerView) v.findViewById(R.id.network_list);
        mEmptyView = (TextView) v.findViewById(android.R.id.empty);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        registerForContextMenu(mRecyclerView);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSearchButtonView = v.findViewById(R.id.searchButton);
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFAB = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        if (mFAB != null)
            mFAB.setImageResource(R.drawable.ic_fab_play);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (goBack)
            goBack();
        setSearchVisibility(false);
        restoreList();
    }

    public void onStop(){
        super.onStop();
        releaseBrowser();
    }

    private void releaseBrowser() {
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    public void onSaveInstanceState(Bundle outState){
        outState.putString(KEY_MRL, mMrl);
        outState.putParcelable(KEY_MEDIA, mCurrentMedia);
        VLCApplication.storeData(KEY_MEDIA_LIST, mediaList);
        VLCApplication.storeData(KEY_CONTENT_LIST, mFoldersContentLists);
        if (mRecyclerView != null) {
            outState.putInt(KEY_POSITION, mLayoutManager.findFirstCompletelyVisibleItemPosition());
        }
        super.onSaveInstanceState(outState);
    }

    public boolean isRootDirectory(){
        return mRoot;
    }

    public String getTitle(){
        if (mRoot)
            return getCategoryTitle();
        else
            return mCurrentMedia != null ? mCurrentMedia.getTitle() : mMrl;
    }

    public String getSubTitle(){
        if (mRoot)
            return null;
        String mrl = Strings.removeFileProtocole(mMrl);
        if (!TextUtils.isEmpty(mrl)) {
            if (this instanceof FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                mrl = getString(R.string.internal_memory)+mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length());
            mrl = Uri.decode(mrl).replaceAll("://", " ").replaceAll("/", " > ");
        }
        return mCurrentMedia != null ? mrl : null;
    }

    @Override
    protected void display() {
        if (!mReadyToDisplay) {
            mReadyToDisplay = true;
            update();
            return;
        }
        updateDisplay();
    }

    public void goBack(){
        if (!mRoot)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    public void browse (MediaWrapper media, int position, boolean save){
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        Fragment next = createFragment();
        Bundle args = new Bundle();
        ArrayList<MediaWrapper> list = mFoldersContentLists != null ? mFoldersContentLists.get(position) : null;
        if (!Util.isListEmpty(list))
            VLCApplication.storeData(KEY_MEDIA_LIST, list);
        args.putParcelable(KEY_MEDIA, media);
        next.setArguments(args);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        if (save)
            ft.addToBackStack(mMrl);
        ft.commit();
    }

    @Override
    public void onMediaAdded(int index, Media media) {
        boolean empty = mAdapter.isEmpty();
        mAdapter.addItem(new MediaWrapper(media), mReadyToDisplay && mRoot, false);
        if (empty && mReadyToDisplay)
            updateEmptyView();
        if (mRoot && (empty || mSwipeRefreshLayout.isRefreshing()))
            mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
    }

    @Override
    public void onMediaRemoved(int index, Media media) {
        mAdapter.removeItem(media.getUri().toString(), mReadyToDisplay);
    }

    @Override
    public void onBrowseEnd() {
        releaseBrowser();
        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        if (mReadyToDisplay)
            display();
        if (!isResumed())
            goBack = true;
    }

    @Override
    public void onRefresh() {
        mSavedPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        refresh();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView(){
        if (mAdapter.isEmpty()){
            if (mSwipeRefreshLayout.isRefreshing()) {
                mEmptyView.setText(R.string.loading);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
            } else {
                mEmptyView.setText(R.string.directory_empty);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setRefreshing(false);
            }
        } else if (mEmptyView.getVisibility() == View.VISIBLE) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    protected void update(){
        update(false);
    }

    protected void update(boolean force){
        if (mReadyToDisplay) {
            updateEmptyView();
            if (force || mAdapter.isEmpty()) {
                refresh();
            } else {
                updateDisplay();
            }
        }
    }

    protected void updateDisplay() {
        if (!mAdapter.isEmpty()) {
            if (mSavedPosition > 0) {
                mLayoutManager.scrollToPositionWithOffset(mSavedPosition, 0);
                mSavedPosition = 0;
            }
        }
        mAdapter.notifyDataSetChanged();
        parseSubDirectories();
        if (mFAB != null) {
            if (mAdapter.getMediaCount() > 0) {
                mFAB.setVisibility(View.VISIBLE);
                mFAB.setOnClickListener(this);
            } else {
                mFAB.setVisibility(View.INVISIBLE);
                mFAB.setOnClickListener(null);
            }
        }
    }

    @Override
    public void refresh() {
        mHandler.sendEmptyMessageDelayed(BrowserFragmentHandler.MSG_SHOW_LOADING, 300);
        mAdapter.clear();
        mFoldersContentLists.clear();
        if (mMediaBrowser == null)
            mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
        else
            mMediaBrowser.changeEventListener(this);
        mCurrentParsedPosition = 0;
        if (mRoot)
            browseRoot();
        else
            mMediaBrowser.browse(mCurrentMedia != null ? mCurrentMedia.getUri() : Uri.parse(mMrl), getBrowserFlags());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                playAll(null);
        }
    }

    protected int getBrowserFlags() {
        return MediaBrowser.Flag.Interact;
    }

    protected static class BrowserFragmentHandler extends WeakHandler<BaseBrowserFragment> {

        static final int MSG_SHOW_LOADING = 0;
        static final int MSG_HIDE_LOADING = 1;
        static final int MSG_REFRESH = 3;

        BrowserFragmentHandler(BaseBrowserFragment owner) {
            super(owner);
        }
        @Override
        public void handleMessage(Message msg) {
            BaseBrowserFragment fragment = getOwner();
            if (fragment == null)
                return;
            switch (msg.what){
                case MSG_SHOW_LOADING:
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                    fragment.updateEmptyView();
                    break;
                case MSG_HIDE_LOADING:
                    removeMessages(MSG_SHOW_LOADING);
                    fragment.mSwipeRefreshLayout.setRefreshing(false);
                    fragment.updateEmptyView();
                    break;
                case MSG_REFRESH:
                    if (getOwner() != null && !getOwner().isDetached())
                        getOwner().refresh();
            }
        }
    }

    public void clear(){
        mAdapter.clear();
    }

    @Override
    protected void inflate(Menu menu, int position) {
        MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        int type = mw.getType();
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(type == MediaWrapper.TYPE_DIR ? R.menu.directory_view_dir : R.menu.directory_view_file, menu);
    }

    protected void setContextMenuItems(Menu menu, int position) {
        MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        int type = mw.getType();
        boolean canWrite = this instanceof FileBrowserFragment && FileUtils.canWrite(mw.getUri().getPath());
        if (type == MediaWrapper.TYPE_DIR) {
            boolean isEmpty = Util.isListEmpty(mFoldersContentLists.get(position));
//                if (canWrite) {
//                    boolean nomedia = new File(mw.getLocation() + "/.nomedia").exists();
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(!nomedia);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(nomedia);
//                } else {
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(false);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(false);
//                }
            menu.findItem(R.id.directory_view_play_folder).setVisible(!isEmpty);
            menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            if (this instanceof NetworkBrowserFragment) {
                MediaDatabase db = MediaDatabase.getInstance();
                if (db.networkFavExists(mw.getUri())) {
                    menu.findItem(R.id.network_remove_favorite).setVisible(true);
                    menu.findItem(R.id.network_edit_favorite).setVisible(!TextUtils.equals(mw.getUri().getScheme(), "upnp"));
                } else
                    menu.findItem(R.id.network_add_favorite).setVisible(true);
            }
        } else {
            boolean canPlayInList =  mw.getType() == MediaWrapper.TYPE_AUDIO ||
                    (mw.getType() == MediaWrapper.TYPE_VIDEO && AndroidUtil.isHoneycombOrLater());
            menu.findItem(R.id.directory_view_play_all).setVisible(canPlayInList);
            menu.findItem(R.id.directory_view_append).setVisible(canPlayInList);
            menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            menu.findItem(R.id.directory_view_info).setVisible(type == MediaWrapper.TYPE_VIDEO || type == MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_view_play_audio).setVisible(type != MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_view_add_playlist).setVisible(type == MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_subtitles_download).setVisible(type == MediaWrapper.TYPE_VIDEO);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void openContextMenu(final int position) {
        mRecyclerView.openContextMenu(position);
    }

    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        int id = item.getItemId();
        if (! (mAdapter.getItem(position) instanceof MediaWrapper))
            return super.onContextItemSelected(item);
        Uri uri = ((MediaWrapper) mAdapter.getItem(position)).getUri();
        MediaWrapper mwFromMl = "file".equals(uri.getScheme()) ? mMediaLibrary.getMedia(uri.getPath()) : null;
        final MediaWrapper mw = mwFromMl != null ? mwFromMl : (MediaWrapper) mAdapter.getItem(position);
        switch (id){
            case R.id.directory_view_play_all:
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                playAll(mw);
                return true;
            case R.id.directory_view_append: {
                if (mService != null)
                    mService.append(mw);
                return true;
            }
            case R.id.directory_view_delete:
                mAdapter.removeItem(position, true);
                UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia(mw, false);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addItem(mw, true, position);
                    }
                });
                return true;
            case  R.id.directory_view_info:
                showMediaInfo(mw);
                return true;
            case R.id.directory_view_play_audio:
                if (mService != null) {
                    mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    mService.load(mw);
                }
                return true;
            case R.id.directory_view_play_folder:
                ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
                boolean videoPlaylist = AndroidUtil.isHoneycombOrLater();
                for (MediaWrapper mediaItem : mFoldersContentLists.get(position)){
                    if (mediaItem.getType() == MediaWrapper.TYPE_AUDIO || (videoPlaylist && mediaItem.getType() == MediaWrapper.TYPE_VIDEO))
                        mediaList.add(mediaItem);
                }
                MediaUtils.openList(getActivity(), mediaList, 0);
                return true;
            case R.id.directory_view_add_playlist:
                ArrayList<MediaWrapper> medias = new ArrayList<>();
                medias.add(mw);
                FragmentManager fm = getActivity().getSupportFragmentManager();
                SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
                Bundle infoArgs = new Bundle();
                infoArgs.putParcelableArrayList(SavePlaylistDialog.KEY_NEW_TRACKS, medias);
                savePlaylistDialog.setArguments(infoArgs);
                savePlaylistDialog.show(fm, "fragment_add_to_playlist");
                return true;
            case R.id.directory_subtitles_download:
                MediaUtils.getSubs(getActivity(), mw);
                return true;
//            case R.id.directory_view_hide_media:
//                try {
//                    if (new File(mw.getLocation()+"/.nomedia").createNewFile())
//                        updateLib();
//                } catch (IOException e) {}
//                return true;
//            case R.id.directory_view_show_media:
//                if (new File(mw.getLocation()+"/.nomedia").delete())
//                    updateLib();
//                return true;

        }
        return false;
    }

    private void showMediaInfo(MediaWrapper mw) {
        BottomSheetDialogFragment bottomSheetDialogFragment = new MediaInfoDialog();
        Bundle args = new Bundle();
        args.putParcelable(MediaInfoDialog.ITEM_KEY, mw);
        bottomSheetDialogFragment.setArguments(args);
        bottomSheetDialogFragment.show(getFragmentManager(), bottomSheetDialogFragment.getTag());
    }

    private void playAll(MediaWrapper mw) {
        boolean isHoneycombOrLater = AndroidUtil.isHoneycombOrLater();
        int positionInPlaylist = 0;
        LinkedList<MediaWrapper> mediaLocations = new LinkedList<>();
        MediaWrapper media;
        for (Object file : mAdapter.getAll())
            if (file instanceof MediaWrapper) {
                media = (MediaWrapper) file;
                if ((isHoneycombOrLater && media.getType() == MediaWrapper.TYPE_VIDEO) || media.getType() == MediaWrapper.TYPE_AUDIO) {
                    mediaLocations.add(media);
                    if (mw != null && media.equals(mw))
                        positionInPlaylist = mediaLocations.size() - 1;
                }
            }
        MediaUtils.openList(getActivity(), mediaLocations, positionInPlaylist);
    }

    protected void parseSubDirectories() {
        if ((mRoot && this instanceof NetworkBrowserFragment) || mCurrentParsedPosition == -1 ||
                mAdapter.isEmpty() || this instanceof FilePickerFragment)
            return;
        mFoldersContentLists.clear();
        if (mMediaBrowser == null)
            mMediaBrowser = new MediaBrowser(VLCInstance.get(), mFoldersBrowserListener);
        else
            mMediaBrowser.changeEventListener(mFoldersBrowserListener);
        mCurrentParsedPosition = 0;
        MediaLibraryItem item;
        MediaWrapper mw;
        while (mCurrentParsedPosition <mAdapter.getItemCount()){
            item = mAdapter.getItem(mCurrentParsedPosition);
            if (item.getItemType() == MediaLibraryItem.TYPE_STORAGE) {
                mw = new MediaWrapper(((Storage) item).getUri());
                mw.setType(MediaWrapper.TYPE_DIR);
            } else  if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA){
                mw = (MediaWrapper) item;
            } else
                mw = null;
            if (mw != null){
                if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST){
                    mMediaBrowser.browse(mw.getUri(), 0);
                    return;
                }
            }
            ++mCurrentParsedPosition;
        }
    }

    private MediaBrowser.EventListener mFoldersBrowserListener = new MediaBrowser.EventListener(){
        ArrayList<MediaWrapper> directories = new ArrayList<>();
        ArrayList<MediaWrapper> files = new ArrayList<>();

        @Override
        public void onMediaAdded(int index, Media media) {
            int type = media.getType();
            if (type == Media.Type.Directory)
                directories.add(new MediaWrapper(media));
            else if (type == Media.Type.File)
                files.add(new MediaWrapper(media));
        }

        @Override
        public void onMediaRemoved(int index, Media media) {}

        @Override
        public void onBrowseEnd() {
            if (mAdapter.isEmpty()) {
                mCurrentParsedPosition = -1;
                releaseBrowser();
                return;
            }
            String holderText = getDescription(directories.size(), files.size());
            MediaWrapper mw = null;

            if (!TextUtils.equals(holderText, "")) {
                mAdapter.setDescription(mCurrentParsedPosition, holderText);
                directories.addAll(files);
                mFoldersContentLists.put(mCurrentParsedPosition, new ArrayList<>(directories));
            }
            while (++mCurrentParsedPosition < mAdapter.getItemCount()){ //skip media that are not browsable
                if (mAdapter.getItem(mCurrentParsedPosition).getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                    mw = (MediaWrapper) mAdapter.getItem(mCurrentParsedPosition);
                    if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST)
                        break;
                } else if (mAdapter.getItem(mCurrentParsedPosition).getItemType() == MediaLibraryItem.TYPE_STORAGE) {
                    mw = new MediaWrapper(((Storage) mAdapter.getItem(mCurrentParsedPosition)).getUri());
                    break;
                } else
                    mw = null;
            }

            if (mw != null) {
                if (mCurrentParsedPosition < mAdapter.getItemCount()) {
                    mMediaBrowser.browse(mw.getUri(), 0);
                } else {
                    mCurrentParsedPosition = -1;
                    releaseBrowser();
                }
            } else
                releaseBrowser();
            directories .clear();
            files.clear();
        }

        private String getDescription(int folderCount, int mediaFileCount) {
            String holderText = "";
            if (folderCount > 0) {
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.subfolders_quantity, folderCount, folderCount
                );
                if (mediaFileCount > 0)
                    holderText += ", ";
            }
            if (mediaFileCount > 0)
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.mediafiles_quantity, mediaFileCount,
                        mediaFileCount);
            else if (folderCount == 0 && mediaFileCount == 0)
                holderText = getString(R.string.directory_empty);
            return holderText;
        }
    };

    @Override
    public boolean enableSearchOption() {
        return !isRootDirectory();
    }

    public Filter getFilter() {
        return mAdapter.getFilter();
    }

    public void restoreList() {
        mAdapter.restoreList();
    }
    public void setSearchVisibility(boolean visible) {
        mSearchButtonView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_browser_file, menu);
        mAdapter.setActionMode(true);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (mAdapter.getSelectedPositions().isEmpty()) {
            stopActionMode();
            return false;
        }
        boolean single = this instanceof FileBrowserFragment && mAdapter.getSelectedPositions().size() == 1;
        int type = single ? ((MediaWrapper) mAdapter.getItem(mAdapter.getSelectedPositions().get(0))).getType() : -1;
        menu.findItem(R.id.action_mode_file_info).setVisible(single && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO));
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_mode_file_play:
                mService.load(mAdapter.getSelection(), 0);
                break;
            case R.id.action_mode_file_append:
                mService.append(mAdapter.getSelection());
                break;
            case R.id.action_mode_file_delete:
                for (MediaWrapper media : mAdapter.getSelection())
                    deleteMedia(media, true);
                break;
            case R.id.action_mode_file_add_playlist:
                UiTools.addToPlaylist(getActivity(), mAdapter.getSelection());
                break;
            case R.id.action_mode_file_info:
                showMediaInfo(mAdapter.getSelection().get(0));
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mAdapter.setActionMode(false);
    }
}
