/*****************************************************************************
 * HistoryAdapter.java
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
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;

/* TODO */
public class HistoryAdapter extends BaseAdapter  {
    public final static String TAG = "VLC/HistoryAdapter";

    private LayoutInflater mInflater;
    private final ArrayList<MediaWrapper> mMediaList;

    public HistoryAdapter(Context context) {
        mInflater = LayoutInflater.from(context);

        mMediaList = new ArrayList<MediaWrapper>();
    }

    @Override
    public int getCount() {
        return mMediaList.size();
    }

    @Override
    public Object getItem(int arg0) {
        return mMediaList.get(arg0).getLocation();
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DirectoryViewHolder holder;
        View v = convertView;

        /* If view not created */
        if (v == null) {
            v = mInflater.inflate(R.layout.list_item, parent, false);
            holder = new DirectoryViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.artist);
            holder.icon = (ImageView) v.findViewById(R.id.cover);
            v.setTag(holder);
        } else
            holder = (DirectoryViewHolder) v.getTag();

        String holderText = "";
        MediaWrapper m = mMediaList.get(position);
        if (m == null )
            return v;

        Log.d(TAG, "Loading media position " + position + " - " + m.getTitle());
        holder.title.setText(m.getTitle());
        holderText = Util.getMediaSubtitle(VLCApplication.getAppContext(), m);

        holder.text.setText(holderText);
        Bitmap b = AudioUtil.getCover(VLCApplication.getAppContext(), m, 64);
        if(b != null)
            holder.icon.setImageBitmap(b);
        else
            holder.icon.setImageResource(R.drawable.icon);

        return v;
    }

    public void remove(int position) {
    }

    private static class DirectoryViewHolder {
        TextView title;
        TextView text;
        ImageView icon;

    }
}
