/*
 * *************************************************************************
 *  MediaLibBrowserFragment.java
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

package org.videolan.vlc.gui.tv.browser;

import android.os.Bundle;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.VLCApplication;

import java.util.concurrent.CyclicBarrier;

public abstract class MediaLibBrowserFragment extends GridFragment {
    protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
    protected Medialibrary mMediaLibrary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
    }

    public void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
        mBarrier.reset();
    }

    public void refresh() {
        if (!mMediaLibrary.isWorking())
            mMediaLibrary.reload();
    }

    public void updateList() {}
}
