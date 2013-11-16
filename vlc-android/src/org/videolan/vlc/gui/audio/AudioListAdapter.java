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

import org.videolan.libvlc.Media;
import org.videolan.vlc.BitmapCache;
import org.videolan.vlc.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AudioListAdapter extends ArrayAdapter<Media> {

    private ArrayList<Media> mMediaList;
    private int mCurrentIndex;

    public AudioListAdapter(Context context) {
        super(context, 0);
        mMediaList = new ArrayList<Media>();
        mCurrentIndex = -1;
    }

    @Override
    public void add(Media m) {
        mMediaList.add(m);
        super.add(m);
    }

    @Override
    public void remove(Media m) {
        mMediaList.remove(m);
        super.remove(m);
    }

    @Override
    public void clear() {
        mMediaList.clear();
        super.clear();
    }

    public void setCurrentIndex(int currentIndex) {
        mCurrentIndex = currentIndex;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_item, parent, false);
            holder = new ViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.cover = (ImageView) v.findViewById(R.id.cover);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.artist = (TextView) v.findViewById(R.id.artist);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media media = getItem(position);

        Bitmap cover = AudioUtil.getCover(v.getContext(), media, 64);
        if (cover == null)
            cover = BitmapCache.GetFromResource(v, R.drawable.icon);

        holder.cover.setImageBitmap(cover);

        holder.title.setText(media.getTitle());
        ColorStateList titleColor = v.getResources().getColorStateList(mCurrentIndex == position
                ? R.color.list_title_last
                : R.color.list_title);
        holder.title.setTextColor(titleColor);
        holder.artist.setText(media.getSubtitle());
        return v;
    }

    public List<String> getLocation(int position) {
        List<String> locations = new ArrayList<String>();
        if (position >= 0 && position < mMediaList.size())
            locations.add(mMediaList.get(position).getLocation());
        return locations;
    }

    public List<String> getLocations() {
        List<String> locations = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            locations.add(mMediaList.get(i).getLocation());
        }
        return locations;
    }

    static class ViewHolder {
        View layout;
        ImageView cover;
        TextView title;
        TextView artist;
    }
}
