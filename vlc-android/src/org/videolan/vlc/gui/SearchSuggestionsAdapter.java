/*
 * *************************************************************************
 *  SearchSuggestionsAdapter.java
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

package org.videolan.vlc.gui;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;

public class SearchSuggestionsAdapter extends CursorAdapter {

    public final static String TAG = "VLC/SearchSuggestionsAdapter";

    MediaLibrary mMediaLibrary = MediaLibrary.getInstance();

    public SearchSuggestionsAdapter(Context context, Cursor cursor){
        super(context, cursor, false);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
        return view;
    }

    @Override
    public void bindView(View view, final Context context, final Cursor cursor) {
        final int position = cursor.getPosition();
        TextView tv = (TextView) view.findViewById(android.R.id.text1);
        tv.setText(cursor.getString(cursor.getColumnIndex(MediaDatabase.MEDIA_TITLE)));
        tv.setBackgroundColor(Util.getColorFromAttribute(context, R.attr.background_menu));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cursor.moveToPosition(position);
                MediaWrapper media = mMediaLibrary.getMediaItem(cursor.getString(cursor.getColumnIndex(MediaDatabase.MEDIA_LOCATION)));
                Util.openMedia(context, media);
            }
        });
    }
}
