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

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.View;

import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.viewmodels.browser.BrowserModel;
import org.videolan.vlc.viewmodels.browser.BrowserModelKt;

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

    protected void setupBrowser() {
        if (isRootDirectory()) viewModel = ViewModelProviders.of(requireActivity(), new BrowserModel.Factory(null, BrowserModelKt.TYPE_FILE, getShowHiddenFiles())).get(BrowserModel.class);
        else viewModel = ViewModelProviders.of(this, new BrowserModel.Factory(getMrl(), BrowserModelKt.TYPE_FILE, getShowHiddenFiles())).get(BrowserModel.class);
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
}
