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
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;

public abstract class MediaBrowserFragment extends PlaybackServiceFragment {

    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected volatile boolean mReadyToDisplay = true;
    protected Medialibrary mMediaLibrary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
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


    protected abstract String getTitle();

    protected String getSubTitle() { return null; }
    public void clear() {}
    protected void display() {}

    protected void inflate(Menu menu, int position) {}
    protected void setContextMenuItems(Menu menu, int position) {}
    protected boolean handleContextItemSelected(MenuItem menu, int position) { return false;}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null)
            return;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo)menuInfo;
        inflate(menu, info.position);

        setContextMenuItems(menu, info.position);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if(!getUserVisibleHint())
            return false;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo) menu.getMenuInfo();

        return info != null && handleContextItemSelected(menu, info.position);
    }
}
