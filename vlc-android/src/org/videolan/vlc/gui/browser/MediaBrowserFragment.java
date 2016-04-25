/*
 * *************************************************************************
 *  MediaBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;

public abstract class MediaBrowserFragment extends PlaybackServiceFragment {

    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected volatile boolean mReadyToDisplay = true;
    protected MediaLibrary mMediaLibrary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = MediaLibrary.getInstance();
    }

    public void onStart(){
        super.onStart();
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(getSubTitle());
            getActivity().supportInvalidateOptionsMenu();
        }
    }

    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReadyToDisplay)
            display();
        else
            mReadyToDisplay = ready;
    }

    protected abstract void display();

    protected abstract String getTitle();
    protected String getSubTitle() { return null; }
    public abstract void clear();
}
