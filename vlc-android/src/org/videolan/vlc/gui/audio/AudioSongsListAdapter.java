/*****************************************************************************
 * AudioSongsListAdapter.java
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
import java.util.List;

import org.videolan.vlc.Media;
import org.videolan.vlc.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AudioSongsListAdapter extends ArrayAdapter<Media> {

    private ArrayList<Media> mMediaList;

    public AudioSongsListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mMediaList = new ArrayList<Media>();
    }

    @Override
    public void add(Media m) {
        mMediaList.add(m);
        super.add(m);
    }

    @Override
    public void clear() {
        mMediaList.clear();
        super.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.artist = (TextView) v.findViewById(R.id.artist);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media media = getItem(position);
        holder.title.setText(media.getTitle());
        holder.artist.setText(media.getArtist() + " - " + media.getAlbum());
        return v;
    }

    public List<String> getPath(int position) {
        List<String> paths = new ArrayList<String>();
        if (position >= 0 && position < mMediaList.size())
            paths.add(mMediaList.get(position).getPath());
        return paths;
    }

    public List<String> getPaths() {
        List<String> paths = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            paths.add(mMediaList.get(i).getPath());
        }
        return paths;
    }

    static class ViewHolder {
        TextView title;
        TextView artist;
    }
}
