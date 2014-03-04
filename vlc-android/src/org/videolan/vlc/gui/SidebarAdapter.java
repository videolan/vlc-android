/*****************************************************************************
 * SidebarAdapter.java
 *****************************************************************************
 * Copyright © 2012-2013 VLC authors and VideoLAN
 * Copyright © 2012-2013 Edward Wang
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
import org.videolan.vlc.gui.video.VideoGridFragment;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.util.Log;
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
    static final List<SidebarEntry> entries;
    private HashMap<String, Fragment> mFragments;

    static {
        SidebarEntry entries2[] = {
            new SidebarEntry( "video", R.string.video, R.drawable.ic_menu_video ),
            new SidebarEntry( "audio", R.string.audio, R.drawable.ic_menu_audio ),
            new SidebarEntry( "directories", R.string.directories, R.drawable.ic_menu_folder ),
            new SidebarEntry( "history", R.string.history, R.drawable.ic_menu_history ),
            //new SidebarEntry( "bookmarks", R.string.bookmarks, R.drawable.ic_bookmarks ),
            //new SidebarEntry( "playlists", R.string.playlists, R.drawable.icon ),
        };
        entries = Arrays.asList(entries2);
    }

    public SidebarAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
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
    public long getItemId(int position) {
        return (long)position; // The SidebarEntry list is unique
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
        Drawable img = VLCApplication.getAppResources().getDrawable(sidebarEntry.drawableID);
        if (img != null) {
            int dp_32 = Util.convertDpToPx(32);
            img.setBounds(0, 0, dp_32, dp_32);
            textView.setCompoundDrawables(img, null, null, null);
        }

        return v;
    }

    public Fragment fetchFragment(String id) {
        if(mFragments.containsKey(id) && mFragments.get(id) != null) {
            return mFragments.get(id);
        }
        Fragment f;
        if(id.equals("audio")) {
            f = new AudioBrowserFragment();
        } else if(id.equals("video")) {
            f = new VideoGridFragment();
        } else if(id.endsWith("directories")) {
            f = new DirectoryViewFragment();
        } else if(id.equals("history")) {
            f = new HistoryFragment();
        }
        else {
            throw new IllegalArgumentException("Wrong fragment id.");
        }
        f.setRetainInstance(true);
        mFragments.put(id, f);
        return f;
    }

    /**
     * When Android has automatically recreated a fragment from the bundle state,
     * use this function to 'restore' the recreated fragment into this sidebar
     * adapter to prevent it from trying to create the same fragment again.
     *
     * @param id ID of the fragment
     * @param f The fragment itself
     */
    public void restoreFragment(String id, Fragment f) {
        if(f == null) {
            Log.e(TAG, "Can't set null fragment for " + id + "!");
            return;
        }
        mFragments.put(id, f);
        // if Android added it, it's been implicitly added already...
    }
}
