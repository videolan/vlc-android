/*
 * *************************************************************************
 *  FileBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.jetbrains.annotations.NotNull;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.hf.OtgAccess;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.browser.BrowserModel;
import org.videolan.vlc.viewmodels.browser.BrowserModelKt;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

public class FileBrowserFragment extends BaseBrowserFragment {

    @Override
    protected Fragment createFragment() {
        return new FileBrowserFragment();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupBrowser();
    }

    private boolean needsRefresh;
    @Override
    public void onStart() {
        super.onStart();
        if (needsRefresh) viewModel.browserRoot();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isRootDirectory() && adapter.isEmpty()) needsRefresh = true;
    }

    @Override
    public void registerSwiperRefreshlayout() {
        if (!isRootDirectory()) super.registerSwiperRefreshlayout();
        else mSwipeRefreshLayout.setEnabled(false);
    }

    protected void setupBrowser() {
        if (isRootDirectory()) viewModel = ViewModelProviders.of(requireActivity(), new BrowserModel.Factory(requireContext(), null, BrowserModelKt.TYPE_FILE, getShowHiddenFiles())).get(BrowserModel.class);
        else viewModel = ViewModelProviders.of(this, new BrowserModel.Factory(requireContext(), getMrl(), BrowserModelKt.TYPE_FILE, getShowHiddenFiles())).get(BrowserModel.class);
    }

    public String getTitle() {
        if (isRootDirectory()) return getCategoryTitle();
        else {
            String title;
            if (getCurrentMedia() != null) {
                if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, Strings.removeFileProtocole(getMrl())))
                    title = getString(R.string.internal_memory);
                else
                    title = this instanceof FilePickerFragment ? getCurrentMedia().getUri().toString() : getCurrentMedia().getTitle();
            } else
                title = this instanceof FilePickerFragment ? getMrl() : FileUtils.getFileNameFromPath(getMrl());
            return title;
        }
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories);
    }

    @Override
    protected void browseRoot() {
        viewModel.browserRoot();
    }

    @Override
    public void onClick(@NotNull View v, int position, @NotNull MediaLibraryItem item) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            final MediaWrapper mw = (MediaWrapper) item;
            if ("otg://".equals(mw.getLocation())) {
                final String title = getString(R.string.otg_device_title);
                final LiveData<Uri> otgRoot = OtgAccess.Companion.getOtgRoot();
                final Uri rootUri = otgRoot.getValue();
                if (rootUri != null && ExternalMonitor.INSTANCE.getDevices().getValue().size() == 1) {
                    browseOtgDevice(rootUri, title);
                } else {
                    otgRoot.observeForever(new Observer<Uri>() {
                        @Override
                        public void onChanged(@Nullable Uri uri) {
                            OtgAccess.Companion.getOtgRoot().removeObserver(this);
                            if (uri != null) browseOtgDevice(uri, title);
                        }
                    });
                    OtgAccess.Companion.requestOtgRoot(requireActivity());
                }
                return;
            }
        }
        super.onClick(v, position, item);
    }

    @Override
    public void onCtxAction(int position, int option) {
        final MediaWrapper mw = (MediaWrapper) getAdapter().getItem(position);
        switch (option) {
            case Constants.CTX_FAV_ADD:
                getBrowserFavRepository().addLocalFavItem(mw.getUri(), mw.getTitle(), mw.getArtworkURL());
                break;
            default:
                super.onCtxAction(position, option);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!(this instanceof FilePickerFragment || this instanceof StorageBrowserFragment))
            inflater.inflate(R.menu.fragment_option_network, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final MenuItem item = menu.findItem(R.id.ml_menu_save);
        if (item == null) return;
        item.setVisible(!isRootDirectory() && getMrl().startsWith("file"));
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final boolean isFavorite = getMrl() != null && getBrowserFavRepository().browserFavExists(Uri.parse(getMrl()));
                WorkersKt.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        item.setIcon(isFavorite ?
                                R.drawable.ic_menu_bookmark_w :
                                R.drawable.ic_menu_bookmark_outline_w);
                        item.setTitle(isFavorite ? R.string.favorites_remove : R.string.favorites_add);
                    }
                });
            }
        });
    }

    private void browseOtgDevice(@NotNull Uri uri, @NotNull String title) {
        final MediaWrapper mw = new MediaWrapper(uri);
        mw.setType(MediaWrapper.TYPE_DIR);
        mw.setTitle(title);
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                browse(mw, true);
            }
        });
    }
}
