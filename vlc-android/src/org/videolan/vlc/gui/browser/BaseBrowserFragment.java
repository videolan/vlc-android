/**
 * **************************************************************************
 * BaseBrowserFragment.java
 * ****************************************************************************
 * Copyright © 2015-2017 VLC authors and VideoLAN
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

import android.arch.lifecycle.Observer;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.DirectoryBrowserBinding;
import org.videolan.vlc.gui.InfoActivity;
import org.videolan.vlc.gui.dialogs.ContextSheetKt;
import org.videolan.vlc.gui.dialogs.CtxActionReceiver;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.viewmodels.browser.BrowserModel;

import java.util.LinkedList;
import java.util.List;

import kotlin.Pair;

public abstract class BaseBrowserFragment extends MediaBrowserFragment<BrowserModel> implements IRefreshable, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, IEventsHandler, CtxActionReceiver {
    protected static final String TAG = "VLC/BaseBrowserFragment";

    public static final String KEY_MRL = "key_mrl";
    public static final String KEY_MEDIA = "key_media";
    public static final String KEY_POSITION = "key_list";

    protected final BrowserFragmentHandler mHandler = new BrowserFragmentHandler(this);
    protected LinearLayoutManager mLayoutManager;
    public String mMrl;
    protected MediaWrapper mCurrentMedia;
    protected int mSavedPosition = -1;
    public boolean mRoot;
    protected boolean goBack = false;
    protected boolean mShowHiddenFiles;
    protected BaseBrowserAdapter mAdapter;

    protected abstract Fragment createFragment();
    protected abstract void browseRoot();
    protected abstract String getCategoryTitle();

    @SuppressWarnings("unchecked")
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) bundle = getArguments();
        if (bundle != null) {
            mCurrentMedia = bundle.getParcelable(KEY_MEDIA);
            if (mCurrentMedia != null) mMrl = mCurrentMedia.getLocation();
            else mMrl = bundle.getString(KEY_MRL);
            mSavedPosition = bundle.getInt(KEY_POSITION);
        } else if (requireActivity().getIntent() != null){
            mMrl = requireActivity().getIntent().getDataString();
            requireActivity().setIntent(null);
        }
        mShowHiddenFiles = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("browser_show_hidden_files", false);
        mRoot = defineIsRoot();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final MenuItem item = menu.findItem(R.id.ml_menu_filter);
        if (item != null) item.setVisible(enableSearchOption());
        final MenuItem sortItem = menu.findItem(R.id.ml_menu_sortby);
        if (sortItem != null) sortItem.setVisible(!mRoot);
    }

    protected boolean defineIsRoot() {
        return mMrl == null;
    }

    protected DirectoryBrowserBinding mBinding;
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DirectoryBrowserBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mAdapter == null) mAdapter = new BaseBrowserAdapter(this);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mBinding.networkList.setLayoutManager(mLayoutManager);
        mBinding.networkList.setAdapter(mAdapter);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        viewModel.getDataset().observe(this, new Observer<List<MediaLibraryItem>>() {
            @Override
            public void onChanged(@Nullable List<MediaLibraryItem> mediaLibraryItems) {
                mAdapter.update(mediaLibraryItems);
            }
        });
        viewModel.getDescriptionUpdate().observe(this, new Observer<Pair<Integer, String>>() {
            @Override
            public void onChanged(@Nullable Pair<Integer, String> pair) {
                if (pair != null) mAdapter.notifyItemChanged(pair.getFirst(), pair.getSecond());
            }
        });
        initFavorites();
    }

    protected void initFavorites() {}

    @Override
    public void onStart() {
        super.onStart();
        if (mFabPlay != null) {
            mFabPlay.setImageResource(R.drawable.ic_fab_play);
            updateFab();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCurrentMedia != null)
        setSearchVisibility(false);
        if (goBack) goBack();
        else restoreList();
    }

    @Override
    public void onStop() {
        super.onStop();
        viewModel.stop();
    }

    public void onSaveInstanceState(@NonNull Bundle outState){
        outState.putString(KEY_MRL, mMrl);
        outState.putParcelable(KEY_MEDIA, mCurrentMedia);
        if (mBinding.networkList != null) outState.putInt(KEY_POSITION, mLayoutManager.findFirstCompletelyVisibleItemPosition());
        super.onSaveInstanceState(outState);
    }

    public boolean isRootDirectory(){
        return mRoot;
    }

    public String getTitle(){
        if (mRoot) return getCategoryTitle();
        else return mCurrentMedia != null ? mCurrentMedia.getTitle() : mMrl;
    }

    public String getSubTitle(){
        if (mRoot) return null;
        String mrl = Strings.removeFileProtocole(mMrl);
        if (!TextUtils.isEmpty(mrl)) {
            if (this instanceof FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                mrl = getString(R.string.internal_memory)+mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length());
            mrl = Uri.decode(mrl).replaceAll("://", " ").replaceAll("/", " > ");
        }
        return mCurrentMedia != null ? mrl : null;
    }

    public boolean goBack(){
        final FragmentActivity activity = getActivity();
        if (activity == null) return false;
        if (!mRoot) activity.getSupportFragmentManager().popBackStack();
        return !mRoot;
    }

    public void browse(MediaWrapper media, boolean save) {
        final FragmentActivity ctx = getActivity();
        if (ctx == null || !isResumed() || isRemoving()) return;
        final FragmentTransaction ft = ctx.getSupportFragmentManager().beginTransaction();
        final Fragment next = createFragment();
        final Bundle args = new Bundle();
        viewModel.saveList(media);
        args.putParcelable(KEY_MEDIA, media);
        next.setArguments(args);
        if (save) ft.addToBackStack(mRoot ? "root" : mMrl);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        ft.commit();
    }

    @Override
    public void onRefresh() {
        mSavedPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        viewModel.refresh();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (mSwipeRefreshLayout == null) return;
        if (Util.isListEmpty(getViewModel().getDataset().getValue())) {
            if (mSwipeRefreshLayout.isRefreshing()) {
                mBinding.empty.setText(R.string.loading);
                mBinding.empty.setVisibility(View.VISIBLE);
                mBinding.networkList.setVisibility(View.GONE);
            } else {
                mBinding.empty.setText(R.string.directory_empty);
                mBinding.empty.setVisibility(View.VISIBLE);
                mBinding.networkList.setVisibility(View.GONE);
            }
        } else if (mBinding.empty.getVisibility() == View.VISIBLE) {
            mBinding.empty.setVisibility(View.GONE);
            mBinding.networkList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void refresh() {
        viewModel.refresh();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                playAll(null);
        }
    }

    static class BrowserFragmentHandler extends WeakHandler<BaseBrowserFragment> {

        static final int MSG_SHOW_LOADING = 0;
        static final int MSG_HIDE_LOADING = 1;
        static final int MSG_REFRESH = 3;

        BrowserFragmentHandler(BaseBrowserFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            final BaseBrowserFragment fragment = getOwner();
            if (fragment == null) return;
            switch (msg.what){
                case MSG_SHOW_LOADING:
                    if (fragment.mSwipeRefreshLayout != null)
                        fragment.mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case MSG_HIDE_LOADING:
                    removeMessages(MSG_SHOW_LOADING);
                    if (fragment.mSwipeRefreshLayout != null)
                        fragment.mSwipeRefreshLayout.setRefreshing(false);
                    break;
                case MSG_REFRESH:
                    removeMessages(MSG_REFRESH);
                    if (!fragment.isDetached()) fragment.refresh();
            }
        }
    }

    public void clear(){
        mAdapter.clear();
    }

    private void removeMedia(final MediaWrapper mw) {
        viewModel.remove(mw);
        final Runnable cancel = new Runnable() {
            @Override
            public void run() {
                viewModel.refresh();
            }
        };
        final View v = getView();
        if (v != null) UiTools.snackerWithCancel(v, getString(R.string.file_deleted), new Runnable() {
            @Override
            public void run() {
                deleteMedia(mw, false, cancel);
            }
        }, cancel);
    }

    private void showMediaInfo(MediaWrapper mw) {
        final Intent i = new Intent(getActivity(), InfoActivity.class);
        i.putExtra(InfoActivity.TAG_ITEM, mw);
        startActivity(i);
    }

    private void playAll(MediaWrapper mw) {
        int positionInPlaylist = 0;
        final LinkedList<MediaWrapper> mediaLocations = new LinkedList<>();
        for (Object file : mAdapter.getAll())
            if (file instanceof MediaWrapper) {
                final MediaWrapper media = (MediaWrapper) file;
                if (media.getType() == MediaWrapper.TYPE_VIDEO || media.getType() == MediaWrapper.TYPE_AUDIO) {
                    mediaLocations.add(media);
                    if (mw != null && media.equals(mw))
                        positionInPlaylist = mediaLocations.size() - 1;
                }
            }
        if (getActivity() != null) MediaUtils.openList(getActivity(), mediaLocations, positionInPlaylist);
    }

    @Override
    public boolean enableSearchOption() {
        return !isRootDirectory();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_browser_file, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        int count = mAdapter.getSelectionCount();
        if (count == 0) {
            stopActionMode();
            return false;
        }
        boolean single = this instanceof FileBrowserFragment && count == 1;
        final List<MediaWrapper> selection = single ? mAdapter.getSelection() : null;
        int type = !Util.isListEmpty(selection) ? selection.get(0).getType() : -1;
        menu.findItem(R.id.action_mode_file_info).setVisible(single && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO));
        menu.findItem(R.id.action_mode_file_append).setVisible(PlaylistManager.Companion.hasMedia());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        List<MediaWrapper> list = mAdapter.getSelection();
        if (!list.isEmpty()) {
            switch (item.getItemId()) {
                case R.id.action_mode_file_play:
                    MediaUtils.openList(getActivity(), list, 0);
                    break;
                case R.id.action_mode_file_append:
                    MediaUtils.appendMedia(getActivity(), list);
                    break;
                case R.id.action_mode_file_add_playlist:
                    UiTools.addToPlaylist(getActivity(), list);
                    break;
                case R.id.action_mode_file_info:
                    showMediaInfo(list.get(0));
                    break;
                default:
                    stopActionMode();
                    return false;
            }
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        int index = -1;
        for (MediaLibraryItem media : mAdapter.getAll()) {
            ++index;
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mAdapter.notifyItemChanged(index, media);
            }
        }
        mAdapter.resetSelectionCount();
    }

    public void onClick(View v, int position, MediaLibraryItem item) {
        final MediaWrapper mediaWrapper = (MediaWrapper) item;
        if (mActionMode != null) {
            if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
                item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
                mAdapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
                mAdapter.notifyItemChanged(position, item);
                invalidateActionMode();
            }
        } else {
            mediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            if (mediaWrapper.getType() == MediaWrapper.TYPE_DIR)
                browse(mediaWrapper, true);
            else
                MediaUtils.openMedia(v.getContext(), mediaWrapper);
        }
    }

    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null || item.getItemType() != MediaLibraryItem.TYPE_MEDIA) return false;
        final MediaWrapper mediaWrapper = (MediaWrapper) item;
        if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
            if (mActionMode != null) return false;
            item.setStateFlags(MediaLibraryItem.FLAG_SELECTED);
            mAdapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
            mAdapter.notifyItemChanged(position, item);
            startActionMode();
        } else onCtxClick(v, position, item);
        return true;
    }

    //TODO run it in VLCIO coroutine context
    public void onCtxClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode == null && item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            final MediaWrapper mw = (MediaWrapper) item;
            int flags = 0;
            if (!mRoot && this instanceof FileBrowserFragment) flags |= Constants.CTX_DELETE;
            if (mw.getType() == MediaWrapper.TYPE_DIR) {
                final boolean isEmpty = viewModel.isFolderEmpty(mw);
                if (!isEmpty) flags |= Constants.CTX_PLAY;
                if (this instanceof NetworkBrowserFragment) {
                    final MediaDatabase db = MediaDatabase.getInstance();
                    if (db.networkFavExists(mw.getUri())) flags |= Constants.CTX_NETWORK_EDIT | Constants.CTX_NETWORK_REMOVE;
                    else flags |= Constants.CTX_NETWORK_ADD;
                }
            } else {
                final boolean isVideo = mw.getType() == MediaWrapper.TYPE_VIDEO;
                final boolean isAudio = mw.getType() == MediaWrapper.TYPE_AUDIO;
                final boolean isMedia = isVideo || isAudio;
                if (isMedia) flags |= Constants.CTX_PLAY_ALL|Constants.CTX_APPEND|Constants.CTX_INFORMATION;
                if (!isAudio) flags |= Constants.CTX_PLAY_AS_AUDIO;
                else flags |= Constants.CTX_ADD_TO_PLAYLIST;
                if (isVideo) flags |= Constants.CTX_DOWNLOAD_SUBTITLES;
            }
            if (flags != 0) ContextSheetKt.showContext(requireActivity(), this, position, item.getTitle(), flags);
        }
    }

    @Override
    public void onCtxAction(int position, int option) {
        if (! (mAdapter.getItem(position) instanceof MediaWrapper))  return;
        final Uri uri = ((MediaWrapper) mAdapter.getItem(position)).getUri();
        final MediaWrapper mwFromMl = "file".equals(uri.getScheme()) ? mMediaLibrary.getMedia(uri) : null;
        final MediaWrapper mw = mwFromMl != null ? mwFromMl : (MediaWrapper) mAdapter.getItem(position);

        switch (option) {
            case Constants.CTX_PLAY:
                MediaUtils.openMedia(getActivity(), mw);
                break;
            case Constants.CTX_PLAY_ALL:
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                playAll(mw);
                break;
            case Constants.CTX_APPEND:
                MediaUtils.appendMedia(getActivity(), mw);
                break;
            case Constants.CTX_DELETE:
                if (checkWritePermission(mw, new Runnable() {
                    @Override
                    public void run() {
                        removeMedia(mw);
                    }
                })) removeMedia(mw);
                break;
            case Constants.CTX_INFORMATION:
                showMediaInfo(mw);
                break;
            case Constants.CTX_PLAY_AS_AUDIO:
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                MediaUtils.openMedia(getActivity(), mw);
                break;
            case Constants.CTX_ADD_TO_PLAYLIST:
                UiTools.addToPlaylist(requireActivity(), mw.getTracks(), SavePlaylistDialog.KEY_NEW_TRACKS);
                break;
            case Constants.CTX_DOWNLOAD_SUBTITLES:
                MediaUtils.getSubs(getActivity(), mw);
                break;
        }
    }

    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        if (mSwipeRefreshLayout != null) mSwipeRefreshLayout.setRefreshing(false);
        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        updateEmptyView();
        if (!Util.isListEmpty(getViewModel().getDataset().getValue())) {
            if (mSavedPosition > 0) {
                mLayoutManager.scrollToPositionWithOffset(mSavedPosition, 0);
                mSavedPosition = 0;
            }
        }
        if (!mRoot) {
            updateFab();
            UiTools.updateSortTitles(this);
        }
    }

    private void updateFab() {
        if (mFabPlay != null) {
            if (mAdapter.getMediaCount() > 0) {
                mFabPlay.setVisibility(View.VISIBLE);
                mFabPlay.setOnClickListener(this);
            } else {
                mFabPlay.setVisibility(View.GONE);
                mFabPlay.setOnClickListener(null);
            }
        }
    }

    public boolean isSortEnabled() {
        return false;
    }
}
