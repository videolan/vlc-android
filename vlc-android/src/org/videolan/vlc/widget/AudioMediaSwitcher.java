/*****************************************************************************
 * AudioMediaSwitcher.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.widget;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


public class AudioMediaSwitcher extends FlingViewGroup {

    public AudioMediaSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateMedia() {
        AudioServiceController audioController = AudioServiceController.getInstance();
        if (audioController == null)
            return;

        removeAllViews();

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (audioController.hasPrevious())
            addMediaView(inflater,
                    audioController.getTitlePrev(),
                    audioController.getArtistPrev(),
                    audioController.getCoverPrev());
        if (audioController.hasMedia())
            addMediaView(inflater,
                    audioController.getTitle(),
                    audioController.getArtist(),
                    audioController.getCover());
        if (audioController.hasNext())
            addMediaView(inflater,
                    audioController.getTitleNext(),
                    audioController.getArtistNext(),
                    audioController.getCoverNext());

        if (audioController.hasPrevious() && audioController.hasMedia()) {
            scrollTo(1);
        }
    }

    private void addMediaView(LayoutInflater inflater, String title, String artist, Bitmap cover) {
        View v = inflater.inflate(R.layout.audio_media_switcher_item, this, false);

        ImageView coverView = (ImageView) v.findViewById(R.id.cover);
        TextView titleView = (TextView) v.findViewById(R.id.title);
        TextView artistView = (TextView) v.findViewById(R.id.artist);

        if (cover != null) {
            coverView.setVisibility(VISIBLE);
            coverView.setImageBitmap(cover);
        }

        titleView.setText(title);
        artistView.setText(artist);

        addView(v);
    }
}
