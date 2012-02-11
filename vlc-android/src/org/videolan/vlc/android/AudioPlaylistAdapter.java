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

package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AudioPlaylistAdapter extends ArrayAdapter<String> {

    private ArrayList<String> mTitles;
    private HashMap<String, ArrayList<Media>> mPlaylists;

    public AudioPlaylistAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mTitles = new ArrayList<String>();
        mPlaylists = new HashMap<String, ArrayList<Media>>();
    }

    public void add(String title, Media media) {
        ArrayList<Media> list;
        if (!mTitles.contains(title)) {
            list = new ArrayList<Media>();
            mPlaylists.put(title, list);
            mTitles.add(title);
            super.add(title);
        } else {
            list = mPlaylists.get(title);
        }
        list.add(media);
    }

    @Override
    public void clear() {
        for (String item : mTitles) {
            mPlaylists.get(item).clear();
            mPlaylists.remove(item);
        }
        mTitles.clear();
        super.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_playlist, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        String name = mTitles.get(position);
        ArrayList<Media> list = mPlaylists.get(name);
        holder.title.setText(name);
        Resources res = getContext().getResources();
        holder.text.setText(res.getQuantityString(R.plurals.songs, list.size(), list.size()));

        return v;
    }

    static class ViewHolder {
        TextView title;
        TextView text;
    }

    public List<String> getPlaylist(int position) {
        List<String> playlist = new ArrayList<String>();
        if (position >= 0 && position < mTitles.size()) {
            List<Media> mediaList = mPlaylists.get(mTitles.get(position));
            for (int i = 0; i < mediaList.size(); i++) {
                playlist.add(mediaList.get(i).getPath());
            }
        }
        return playlist;

    }
}
