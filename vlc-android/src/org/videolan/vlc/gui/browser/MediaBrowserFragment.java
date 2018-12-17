/*
 * *************************************************************************
 *  MediaBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015-2016 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.ContentActivity;
import org.videolan.vlc.gui.InfoActivity;
import org.videolan.vlc.gui.audio.BaseAudioBrowser;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.WriteExternalDelegate;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.SortableModel;

import java.util.LinkedList;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

public abstract class MediaBrowserFragment<T extends SortableModel> extends Fragment implements androidx.appcompat.view.ActionMode.Callback, Filterable {

    public final static String TAG = "VLC/MediaBrowserFragment";

    protected View mSearchButtonView;
    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected Medialibrary mMediaLibrary;
    protected ActionMode mActionMode;
    public FloatingActionButton mFabPlay;
    protected T viewModel;

    public T getViewModel() {
        return viewModel;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        setHasOptionsMenu(!AndroidDevices.isAndroidTv);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSearchButtonView = view.findViewById(R.id.searchButton);
        mSwipeRefreshLayout = view.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        if (hasFAB()) mFabPlay = requireActivity().findViewById(R.id.fab);
        setBreadcrumb();
    }

    protected boolean hasFAB() {
        return mSwipeRefreshLayout != null;
    }

    protected void setBreadcrumb() {
        final RecyclerView ariane = requireActivity().findViewById(R.id.ariane);
        if (ariane != null) ariane.setVisibility(View.GONE);
    }

    public void onStart() {
        super.onStart();
        updateActionBar();
        if (mFabPlay != null) {
            setFabPlayVisibility(true);
            mFabPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFabPlayClick(v);
                }
            });
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        setFabPlayVisibility(false);
    }

    public void updateActionBar() {
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity == null) return;
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(getSubTitle());
            activity.supportInvalidateOptionsMenu();
        }
        if (activity instanceof ContentActivity) ((ContentActivity)activity).toggleAppBarElevation(!(this instanceof BaseAudioBrowser));
    }

    @Override
    public void onPause() {
        super.onPause();
        stopActionMode();
    }

    public void setFabPlayVisibility(boolean enable) {
        if (mFabPlay != null) {
            if (enable) mFabPlay.show();
            else mFabPlay.hide();
        }
    }

    public void onFabPlayClick(View view) {}

    public abstract String getTitle();
    public abstract void onRefresh();

    protected String getSubTitle() { return null; }
    public void clear() {}

    protected boolean removeItem(final MediaLibraryItem item) {
        final View view = getView();
        if (view == null) return false;
        if (item.getItemType() == MediaLibraryItem.TYPE_PLAYLIST) {
            UiTools.snackerConfirm(getView(), getString(R.string.confirm_delete_playlist, item.getTitle()), new Runnable() {
                @Override
                public void run() {
                    MediaUtils.INSTANCE.deletePlaylist((Playlist) item);
                }
            });
        } else if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            final Runnable deleteAction = new Runnable() {
                @Override
                public void run() {
                    deleteMedia(item, false, null);
                }
            };
            final int resid = ((MediaWrapper)item).getType() == MediaWrapper.TYPE_DIR ? R.string.confirm_delete_folder : R.string.confirm_delete;
            UiTools.snackerConfirm(getView(), getString(resid, item.getTitle()), new Runnable() {
                @Override
                public void run() {
                    if (checkWritePermission((MediaWrapper) item, deleteAction)) deleteAction.run();
                }
            });
        } else return false;
        return true;
    }

    protected void deleteMedia(final MediaLibraryItem mw, final boolean refresh, final Runnable failCB) {
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final LinkedList<String> foldersToReload = new LinkedList<>();
                final LinkedList<String> mediaPaths = new LinkedList<>();
                for (MediaWrapper media : mw.getTracks()) {
                    final String path = media.getUri().getPath();
                    final String parentPath = FileUtils.getParent(path);
                    if (FileUtils.deleteFile(media.getUri())) {
                        if (media.getId() > 0L && !foldersToReload.contains(parentPath)) {
                            foldersToReload.add(parentPath);
                        }
                        mediaPaths.add(media.getLocation());
                    } else onDeleteFailed(media);
                }
                for (String folder : foldersToReload) mMediaLibrary.reload(folder);
                if (getActivity() != null) {
                    WorkersKt.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mediaPaths.isEmpty()) {
                                if (failCB != null) failCB.run();
                                return;
                            }
                            if (refresh) onRefresh();
                        }
                    });
                }
            }
        });
    }

    protected boolean checkWritePermission(MediaWrapper media, Runnable callback) {
        final Uri uri = media.getUri();
        if (!"file".equals(uri.getScheme())) return false;
        if (uri.getPath().startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            //Check write permission starting Oreo
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage()) {
                Permissions.askWriteStoragePermission(getActivity(), false, callback);
                return false;
            }
        } else if (AndroidUtil.isLolliPopOrLater && WriteExternalDelegate.Companion.needsWritePermission(uri)) {
            WriteExternalDelegate.Companion.askForExtWrite(getActivity(), uri, callback);
            return false;
        }
        return true;
    }

    private void onDeleteFailed(MediaWrapper media) {
        final View v = getView();
        if (v != null && isAdded()) UiTools.snacker(v, getString(R.string.msg_delete_failed, media.getTitle()));
    }

    protected void showInfoDialog(MediaLibraryItem item) {
        final Intent i = new Intent(getActivity(), InfoActivity.class);
        i.putExtra(InfoActivity.TAG_ITEM, item);
        startActivity(i);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_sortby).setVisible(getViewModel().canSortByName());
        menu.findItem(R.id.ml_menu_sortby_filename).setVisible(getViewModel().canSortByFileNameName());
        menu.findItem(R.id.ml_menu_sortby_artist_name).setVisible(getViewModel().canSortByArtist());
        menu.findItem(R.id.ml_menu_sortby_album_name).setVisible(getViewModel().canSortByAlbum());
        menu.findItem(R.id.ml_menu_sortby_length).setVisible(getViewModel().canSortByDuration());
        menu.findItem(R.id.ml_menu_sortby_date).setVisible(getViewModel().canSortByReleaseDate());
        menu.findItem(R.id.ml_menu_sortby_last_modified).setVisible(getViewModel().canSortByLastModified());
        menu.findItem(R.id.ml_menu_sortby_number).setVisible(false);
        UiTools.updateSortTitles(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
                sortBy(Medialibrary.SORT_ALPHA);
                return true;
            case R.id.ml_menu_sortby_filename:
                sortBy(Medialibrary.SORT_FILENAME);
                return true;
            case R.id.ml_menu_sortby_length:
                sortBy(Medialibrary.SORT_DURATION);
                return true;
            case R.id.ml_menu_sortby_date:
                sortBy(Medialibrary.SORT_RELEASEDATE);
                return true;
            case R.id.ml_menu_sortby_last_modified:
                sortBy(Medialibrary.SORT_LASTMODIFICATIONDATE);
                return true;
            case R.id.ml_menu_sortby_artist_name:
                sortBy(Medialibrary.SORT_ARTIST);
                return true;
            case R.id.ml_menu_sortby_album_name:
                sortBy(Medialibrary.SORT_ALBUM);
                return true;
            case R.id.ml_menu_sortby_number:
                sortBy(Medialibrary.SORT_FILESIZE); //TODO
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void sortBy(int sort) {
        getViewModel().sort(sort);
    }

    public Menu getMenu() {
        final FragmentActivity activity = getActivity();
        if (!(activity instanceof AudioPlayerContainerActivity)) return null;
        return ((AudioPlayerContainerActivity)activity).getMenu();

    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void startActionMode() {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) return;
        mActionMode = activity.startSupportActionMode(this);
        setFabPlayVisibility(false);
    }

    protected void stopActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            onDestroyActionMode(mActionMode);
            setFabPlayVisibility(true);
        }
    }

    public void invalidateActionMode() {
        if (mActionMode != null) mActionMode.invalidate();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void filter(String query) {
        getViewModel().filter(query);
    }

    public void restoreList() {
        getViewModel().restore();
    }

    @Override
    public boolean enableSearchOption() {
        return true;
    }

    @Override
    public String getFilterQuery() {
        return getViewModel().getFilterQuery();
    }

    @Override
    public void setSearchVisibility(boolean visible) {
        if ((mSearchButtonView.getVisibility() == View.VISIBLE) == visible) return;
        if (mSearchButtonView.getParent() instanceof ConstraintLayout) {
            final ConstraintLayout cl = (ConstraintLayout) mSearchButtonView.getParent();
            final ConstraintSet cs = new ConstraintSet();
            cs.clone(cl);
            cs.setVisibility(R.id.searchButton, visible ? ConstraintSet.VISIBLE : ConstraintSet.GONE);
            TransitionManager.beginDelayedTransition(cl);
            cs.applyTo(cl);
        } else UiTools.setViewVisibility(mSearchButtonView, visible ? View.VISIBLE : View.GONE);
    }
}
