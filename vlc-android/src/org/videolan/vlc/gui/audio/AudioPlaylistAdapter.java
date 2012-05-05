/*****************************************************************************
 * AudioPlaylistAdapter.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AudioPlaylistAdapter extends BaseExpandableListAdapter {

    private Context mContext;
    private LayoutInflater mInflater;
    private final int mGroupTextId;
    private final int mChildTextId;
    private ArrayList<String> mTitles;
    private HashMap<String, ArrayList<String>> mSubTitles;
    private HashMap<String, HashMap<String, ArrayList<Media>>> mGroups;

    public AudioPlaylistAdapter(Context context, int groupTextId, int childTextId) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mGroupTextId = groupTextId;
        mChildTextId = childTextId;
        mTitles = new ArrayList<String>();
        mSubTitles = new HashMap<String, ArrayList<String>>();
        mGroups = new HashMap<String, HashMap<String, ArrayList<Media>>>();
    }

    public void add(String title, String subtitle, Media media) {
        ArrayList<String> subtitles;
        HashMap<String, ArrayList<Media>> group;
        ArrayList<Media> list;

        if (!mSubTitles.containsKey(title)) {
            subtitles = new ArrayList<String>();
            group = new HashMap<String, ArrayList<Media>>();
            mTitles.add(title);
            mSubTitles.put(title, subtitles);
            mGroups.put(title, group);
        }
        else {
            subtitles = mSubTitles.get(title);
            group = mGroups.get(title);
        }

        if (!group.containsKey(subtitle)) {
            list = new ArrayList<Media>();
            subtitles.add(subtitle);
            group.put(subtitle, list);
        }
        else {
            list = group.get(subtitle);
        }

        list.add(media);
    }

    public void clear() {
        for (String item : mTitles) {
            ArrayList<String> subtitles = mSubTitles.get(item);
            HashMap<String, ArrayList<Media>> subgroups = mGroups.get(item);
            for (String subitem : subtitles) {
                subgroups.get(subitem).clear();
                subgroups.remove(subitem);
            }
            subtitles.clear();
            mSubTitles.remove(item);
            mGroups.remove(item);
        }
        mTitles.clear();
    }

    @Override
    public int getGroupCount() {
        return mGroups.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public String getGroup(int groupPosition) {
        return mTitles.get(groupPosition);
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        String key = mTitles.get(groupPosition);
        int count = mSubTitles.get(key).size();
        return count > 2 ? count : 0;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public String getChild(int groupPosition, int childPosition) {
        String key = mTitles.get(groupPosition);
        return mSubTitles.get(key).get(childPosition);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupViewHolder holder;
        View v = convertView;
        if (v == null) {
            v = mInflater.inflate(R.layout.audio_browser_playlist, parent, false);
            holder = new GroupViewHolder();
            holder.layout = (View) v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            holder.more = (ImageView) v.findViewById(R.id.more);
            v.setTag(holder);
        } else
            holder = (GroupViewHolder) v.getTag();

        String name = mTitles.get(groupPosition);
        int count = mSubTitles.get(name).size();
        int countMedia = mGroups.get(name).get(null).size();
        Resources res = mContext.getResources();

        Util.setItemBackground(holder.layout, groupPosition);
        holder.title.setText(name);
        if (count > 2)
            holder.text.setText(res.getQuantityString(mGroupTextId, count - 1, count - 1));
        else if (count == 2)
            holder.text.setText(String.format("%s - %s",
                    mSubTitles.get(name).get(1),
                    res.getQuantityString(mChildTextId, countMedia, countMedia)));
        else
            holder.text.setText(res.getQuantityString(mChildTextId, countMedia, countMedia));

        holder.more.setVisibility(count > 2 ? View.VISIBLE : View.GONE);
        holder.more.setImageResource(isExpanded ? R.drawable.ic_up : R.drawable.ic_down);

        return v;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        ChildViewHolder holder;
        View v = convertView;
        if (v == null) {
            v = mInflater.inflate(R.layout.audio_browser_playlist_child, parent, false);
            holder = new ChildViewHolder();
            holder.layout = (View) v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            v.setTag(holder);
        } else
            holder = (ChildViewHolder) v.getTag();

        String key = mTitles.get(groupPosition);
        String name = mSubTitles.get(key).get(childPosition);
        ArrayList<Media> list = mGroups.get(key).get(name);
        int count = list.size();
        Resources res = mContext.getResources();

        Util.setItemBackground(holder.layout, childPosition);
        if (name != null)
            holder.title.setText(name);
        else
            holder.title.setText(R.string.all_albums);
        holder.text.setText(res.getQuantityString(mChildTextId, count, count));

        return v;
    }

    static class GroupViewHolder {
        View layout;
        TextView title;
        TextView text;
        ImageView more;
    }

    static class ChildViewHolder {
        View layout;
        TextView title;
        TextView text;
    }

    public List<String> getPlaylist(int groupPosition, int childPosition) {
        List<String> playlist = new ArrayList<String>();
        if (groupPosition >= 0 && groupPosition < mTitles.size()) {
            String key = mTitles.get(groupPosition);
            if (childPosition >= 0 && childPosition < mSubTitles.get(key).size()) {
                String subkey = mSubTitles.get(key).get(childPosition);
                List<Media> mediaList = mGroups.get(key).get(subkey);
                for (int i = 0; i < mediaList.size(); i++) {
                    playlist.add(mediaList.get(i).getLocation());
                }
            }
        }
        return playlist;
    }
}
