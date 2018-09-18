/*
 * ************************************************************************
 *  MediaSortedFragment.java
 * *************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.viewmodels.BaseModel;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public abstract class MediaSortedFragment<T extends BaseModel<? extends MediaLibraryItem>> extends CategoriesFragment<T> {
    protected Uri mUri;
    protected boolean mShowHiddenFiles = false;


    protected String getKey() {
        return mUri != null ? Constants.CURRENT_BROWSER_MAP+mUri.getPath() : Constants.CURRENT_BROWSER_MAP;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(Constants.KEY_URI);
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null)
                mUri = intent.getData();
        }
        mShowHiddenFiles = Settings.INSTANCE.getInstance(requireContext()).getBoolean("browser_show_hidden_files", false);
    }

    public void onPause(){
        super.onPause();
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null) outState.putParcelable(Constants.KEY_URI, mUri);
    }
}
