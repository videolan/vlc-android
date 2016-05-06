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

package org.videolan.vlc.gui.helpers;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

public class SearchSuggestionsAdapter extends CursorAdapter {

    public final static String TAG = "VLC/SearchSuggestionsAdapter";
    private static int backgroundColor;

    MediaLibrary mMediaLibrary = MediaLibrary.getInstance();

    public SearchSuggestionsAdapter(Context context, Cursor cursor){
        super(context, cursor, false);
        backgroundColor = UiTools.getColorFromAttribute(context, R.attr.background_menu);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.audio_browser_item, parent, false);
        return view;
    }

    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        final String location = cursor.getString(cursor.getColumnIndex(MediaDatabase.MEDIA_LOCATION));
        final MediaWrapper mw = mMediaLibrary.getMediaItem(location);
        view.findViewById(R.id.item_more).setVisibility(View.GONE);
        TextView tv1 = (TextView) view.findViewById(R.id.title);
        tv1.setText(cursor.getString(cursor.getColumnIndex(MediaDatabase.MEDIA_TITLE)));
        view.setBackgroundColor(backgroundColor);

        if (mw == null)
            return;
        String artist = mw.getAlbumArtist();
        if (artist == null)
            artist = mw.getArtist();
        if (artist != null) {
            TextView tv2 = (TextView) view.findViewById(R.id.subtitle);
            tv2.setText(artist);
        } else
            view.findViewById(R.id.subtitle).setVisibility(View.GONE);

        Bitmap artwork;
        ImageView coverView = (ImageView) view.findViewById(R.id.media_cover);
        if (mw.getType() == MediaWrapper.TYPE_AUDIO)
            artwork = AudioUtil.getCover(context, mw, context.getResources().getDimensionPixelSize(R.dimen.audio_browser_item_size));
        else if (mw.getType() == MediaWrapper.TYPE_VIDEO)
            artwork = BitmapUtil.getPicture(mw);
        else
            artwork = null;
        if (artwork != null) {
            coverView.setVisibility(View.VISIBLE);
            coverView.setImageBitmap(artwork);
        } else
            coverView.setVisibility(View.INVISIBLE);


        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaUtils.openMedia(context, mw);
            }
        });
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    UiTools.setKeyboardVisibility(v, false);
                return false;
            }
        });
    }
}
