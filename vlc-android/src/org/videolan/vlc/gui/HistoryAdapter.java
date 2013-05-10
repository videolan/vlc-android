/*****************************************************************************
 * HistoryAdapter.java
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.WeakHandler;

import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class HistoryAdapter extends BaseAdapter {
    public final static String TAG = "VLC/HistoryAdapter";

    private LayoutInflater mInflater;
    private List<String> mHistory;

    public HistoryAdapter() {
        mInflater = LayoutInflater.from(VLCApplication.getAppContext());
        mHistory = new ArrayList<String>();

        LibVLC libVLC = LibVLC.getExistingInstance();
        if (libVLC != null)
            libVLC.getMediaListItems((ArrayList<String>) mHistory);

        EventHandler em = EventHandler.getInstance();
        em.addHandler(new HistoryEventHandler(this));
    }

    @Override
    public int getCount() {
        return mHistory.size();
    }

    @Override
    public Object getItem(int arg0) {
        return mHistory.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String selected = mHistory.get(position);
        DirectoryAdapter.DirectoryViewHolder holder;
        View v = convertView;

        /* If view not created */
        if (v == null) {
            v = mInflater.inflate(R.layout.directory_view_item, parent, false);
            holder = new DirectoryAdapter.DirectoryViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            holder.icon = (ImageView) v.findViewById(R.id.dvi_icon);
            v.setTag(holder);
        } else
            holder = (DirectoryAdapter.DirectoryViewHolder) v.getTag();

        Util.setItemBackground(holder.layout, position);

        String holderText = "";
        Log.d(TAG, "Loading media position " + position + " - " + selected);
        Media m = new Media(selected, position);
        holder.title.setText(m.getTitle());
        holderText = m.getSubtitle();

        holder.text.setText(holderText);
        holder.icon.setImageResource(R.drawable.icon);

        return v;
    }

    /**
     * The media list changed.
     *
     * @param added Set to true if the media list was added to
     * @param uri The URI added/removed
     * @param index The index added/removed at
     */
    public void updateEvent(Boolean added, String uri, int index) {
        if(added) {
            Log.v(TAG, "Added index " + index + ": " + uri);
            mHistory.add(index, uri);
        } else {
            Log.v(TAG, "Removed index " + index + ": " + uri);
            mHistory.remove(index);
        }
        notifyDataSetChanged();
    }

    public List<String> getAllURIs() {
        return Collections.unmodifiableList(mHistory);
    }

    public void refresh() {
        ArrayList<String> s = new ArrayList<String>();
        LibVLC libVLC = LibVLC.getExistingInstance();
        if (libVLC != null) {
            libVLC.getMediaListItems(s);
            mHistory.clear();
            mHistory = s;
            this.notifyDataSetChanged();
        }
    }

    /**
     *  Handle changes to the media list
     */
    private static class HistoryEventHandler extends WeakHandler<HistoryAdapter> {
        public HistoryEventHandler(HistoryAdapter owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            HistoryAdapter adapater = getOwner();
            if(adapater == null) return;

            String item_uri = msg.getData().getString("item_uri");
            int item_index = msg.getData().getInt("item_index");
            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaListItemAdded:
                    adapater.updateEvent(true, item_uri, item_index);
                    break;
                case EventHandler.MediaListItemDeleted:
                    adapater.updateEvent(false, item_uri, item_index);
                    break;
            }
        }
    };
}
