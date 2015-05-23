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

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.browser.FileBrowserFragment;
import org.videolan.vlc.gui.browser.NetworkBrowserFragment;
import org.videolan.vlc.gui.network.MRLPanelFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SidebarAdapter extends BaseAdapter {
    public final static String TAG = "VLC/SidebarAdapter";

    public static class SidebarEntry {
        public static final  int TYPE_FRAGMENT = 0;
        public static final  int TYPE_ACTION = 1;
        public static final  int TYPE_SECONDARY_FRAGMENT = 2;

        public static final String ID_VIDEO = "video";
        public static final String ID_AUDIO = "audio";
        public static final String ID_NETWORK = "network";
        public static final String ID_DIRECTORIES = "directories";
        public static final String ID_HISTORY = "history";
        public static final String ID_MRL = "mrl";
        public static final String ID_PREFERENCES = "preferences";
        public static final String ID_ABOUT = "about";

        String id;
        String name;
        int attributeID;
        int type;

        public SidebarEntry(String id, int name, int attributeID, int type) {
            this.id = id;
            this.name = VLCApplication.getAppContext().getString(name);
            this.attributeID = attributeID;
            this.type = type;
        }
    }

    private Context mContext;
    private LayoutInflater mInflater;
    static final List<SidebarEntry> entries;
    public static final List<String> sidebarFragments;
    private HashMap<String, Fragment> mFragments;
    private String mCurrentFragmentId;

    static {
        entries = new ArrayList<SidebarEntry>();
        entries.add(new SidebarEntry(SidebarEntry.ID_VIDEO, R.string.video, R.attr.ic_menu_video, SidebarEntry.TYPE_FRAGMENT));
        entries.add(new SidebarEntry(SidebarEntry.ID_AUDIO, R.string.audio, R.attr.ic_menu_audio, SidebarEntry.TYPE_FRAGMENT));
        entries.add(new SidebarEntry(SidebarEntry.ID_DIRECTORIES, R.string.directories, R.attr.ic_menu_folder, SidebarEntry.TYPE_FRAGMENT));
        if (BuildConfig.DEBUG)
            entries.add(new SidebarEntry(SidebarEntry.ID_NETWORK, R.string.network_browsing, R.attr.ic_menu_network, SidebarEntry.TYPE_FRAGMENT));
        entries.add(new SidebarEntry(SidebarEntry.ID_MRL, R.string.open_mrl, R.attr.ic_menu_openmrl, SidebarEntry.TYPE_FRAGMENT));
        if (BuildConfig.DEBUG)
            entries.add(new SidebarEntry(SidebarEntry.ID_HISTORY, R.string.history, R.attr.ic_menu_history, SidebarEntry.TYPE_FRAGMENT));
        sidebarFragments = new ArrayList<String>();
        entries.add(new SidebarEntry(SidebarEntry.ID_PREFERENCES, R.string.preferences, R.attr.ic_menu_preferences, SidebarEntry.TYPE_ACTION));
        entries.add(new SidebarEntry(SidebarEntry.ID_ABOUT, R.string.about, R.attr.ic_menu_cone, SidebarEntry.TYPE_SECONDARY_FRAGMENT));
        for(SidebarEntry e : entries) {
            sidebarFragments.add(e.id);
        }
    }

    public SidebarAdapter(Context context) {
        mContext = context;
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
        return position; // The SidebarEntry list is unique
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
        Drawable img = VLCApplication.getAppResources().getDrawable(
                Util.getResourceFromAttribute(mContext, sidebarEntry.attributeID));
        if (img != null) {
            int dp_32 = Util.convertDpToPx(32);
            img.setBounds(0, 0, dp_32, dp_32);
            textView.setCompoundDrawables(img, null, null, null);
        }
        // Set in selected the current item.
        if (TextUtils.equals(mCurrentFragmentId,sidebarEntry.id)) {
            textView.setTypeface(null, Typeface.BOLD);
        } else {
            textView.setTypeface(null, Typeface.NORMAL);
        }

        return v;
    }

    public Fragment fetchFragment(String id) {
        // Save the previous fragment in case an error happens after.
        String prevFragmentId = mCurrentFragmentId;

        // Set the current fragment.
        setCurrentFragment(id);

        if(mFragments.containsKey(id) && mFragments.get(id) != null) {
            return mFragments.get(id);
        }

        Fragment f;
        if(id.equals(SidebarEntry.ID_AUDIO)) {
            f = new AudioBrowserFragment();
        } else if(id.equals(SidebarEntry.ID_VIDEO)) {
            f = new VideoGridFragment();
        } else if(id.endsWith(SidebarEntry.ID_DIRECTORIES)) {
            f = new FileBrowserFragment();
        } else if(id.equals(SidebarEntry.ID_HISTORY)) {
            f = new HistoryFragment();
        } else if(id.equals(SidebarEntry.ID_MRL)) {
            f = new MRLPanelFragment();
        } else if(id.equals(SidebarEntry.ID_NETWORK)) {
            f = new NetworkBrowserFragment();
        }
        else {
            mCurrentFragmentId = prevFragmentId; // Restore the current fragment id.
            throw new IllegalArgumentException("Wrong fragment id.");
        }
        f.setRetainInstance(true);
        mFragments.put(id, f);
        return f;
    }

    public void setCurrentFragment(String id) {
        mCurrentFragmentId = id;
        this.notifyDataSetChanged();
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
        setCurrentFragment(id);
        // if Android added it, it's been implicitly added already...
    }
}
