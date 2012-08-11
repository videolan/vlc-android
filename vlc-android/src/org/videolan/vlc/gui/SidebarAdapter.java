/*****************************************************************************
 * SidebarAdapter.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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
package org.videolan.vlc.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.video.VideoListFragment;

import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class SidebarAdapter extends BaseAdapter {
    public final static String TAG = "VLC/SidebarAdapter";

    static class SidebarEntry {
        String id;
        String name;
        int drawableID;

        public SidebarEntry(String _id, String _name, int _drawableID) {
            this.id = _id;
            this.name = _name;
            this.drawableID = _drawableID;
        }

        public SidebarEntry(String _id, int _name, int _drawableID) {
            this.id = _id;
            this.name = VLCApplication.getAppContext().getString(_name);
            this.drawableID = _drawableID;
        }
    }

    private LayoutInflater mInflater;
    private FragmentManager mFragmentManager;
    static final List<SidebarEntry> entries;
    private HashMap<String, Fragment> mFragments;

    static {
        SidebarEntry entries2[] = {
            new SidebarEntry( "audio", R.string.audio, R.drawable.header_icon_audio ),
            new SidebarEntry( "video", R.string.video, R.drawable.header_icon_video ),
            new SidebarEntry( "directories", R.string.directories, R.drawable.ic_folder ),
            new SidebarEntry( "history", "History", android.R.drawable.ic_menu_recent_history ),
            new SidebarEntry( "bookmarks", "Bookmarks", R.drawable.ic_bookmarks ),
            new SidebarEntry( "playlists", "Playlists", R.drawable.icon ),
        };
        entries = Arrays.asList(entries2);
    }

    public SidebarAdapter(FragmentManager fm) {
        mInflater = LayoutInflater.from(VLCApplication.getAppContext());
        mFragmentManager = fm;
        mFragments = new HashMap<String, Fragment>(entries.size());
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public Object getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        SidebarEntry sidebarEntry = entries.get(position);

        /* If view not created */
        if(v == null) {
            v = mInflater.inflate(R.layout.sidebar_item, parent, false);
        }
        TextView textView = (TextView)v;
        textView.setText(sidebarEntry.name);
        Drawable img = VLCApplication.getAppContext().getResources().getDrawable(sidebarEntry.drawableID);
        int dp_32 = Util.convertDpToPx(32);
        img.setBounds(0, 0, dp_32, dp_32);
        textView.setCompoundDrawables(img, null, null, null);

        return v;
    }

    public Fragment getFragment(String id) {
        if(mFragments.containsKey(id) && mFragments.get(id) != null) {
            return mFragments.get(id);
        }
        Fragment f;
        if(id.equals("audio")) {
            f = new AudioBrowserFragment();
        } else if(id.equals("video")) {
            f = new VideoListFragment();
        } else if(id.endsWith("directories")) {
            f = new DirectoryViewFragment();
        } else { /* TODO */
            f = new AboutLicenceFragment();
        }
        f.setRetainInstance(true);
        mFragmentManager.beginTransaction()
            .add(R.id.fragment_placeholder, f, id)
            .commitAllowingStateLoss();
        mFragments.put(id, f);
        return f;
    }
}
