/*****************************************************************************
 * MediaInfoActivity.java
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

package org.videolan.vlc.gui.video;

import java.nio.ByteBuffer;

import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.TrackInfo;
import org.videolan.vlc.Util;

import android.app.ListActivity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaInfoActivity extends ListActivity {
    public final static String TAG = "VLC/MediaInfoActivity";
    private Media mItem;
    private Bitmap mImage;
    private TrackInfo[] mTracks;
    private MediaInfoAdapter mAdapter;
    private final static int NEW_IMAGE = 0;
    private final static int NEW_TEXT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_info);
        String path = getIntent().getExtras().getString("filePath");
        if (path == null)
            return;
        mItem = MediaLibrary.getInstance(this).getMediaItem(path);

        // set title
        TextView titleView = (TextView) findViewById(R.id.title);
        titleView.setText(mItem.getTitle());

        // set length
        TextView lengthView = (TextView) findViewById(R.id.length);
        lengthView.setText(Util.millisToString(mItem.getLength()));

        mAdapter = new MediaInfoAdapter(MediaInfoActivity.this, R.layout.audio_browser_playlist);
        setListAdapter(mAdapter);

        new Thread(mLoadImage).start();
    }

    Runnable mLoadImage = new Runnable() {
        @Override
        public void run() {
            LibVLC mLibVlc = null;
            try {
                mLibVlc = LibVLC.getInstance();
            } catch (LibVlcException e) {
                return;
            }

            mTracks = mLibVlc.readTracksInfo(mItem.getPath());
            mHandler.sendEmptyMessage(NEW_TEXT);

            int width = Math.min(getWindowManager().getDefaultDisplay().getWidth(),
                    getWindowManager().getDefaultDisplay().getHeight());
            int height = width * 9 / 16;

            // Get the thumbnail.
            mImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            byte[] b = mLibVlc.getThumbnail(mItem.getPath(), width, height);

            if (b == null) // We were not able to create a thumbnail for this item.
                return;

            mImage.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            mImage = Util.cropBorders(mImage, width, height);

            mHandler.sendEmptyMessage(NEW_IMAGE);
        }
    };

    Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_IMAGE:
                    ImageView imageView = (ImageView) MediaInfoActivity.this.findViewById(R.id.image);
                    imageView.setImageBitmap(mImage);
                    break;
                case NEW_TEXT:
                    for (TrackInfo track : mTracks) {
                        mAdapter.add(track);
                    }
                    break;
            }
        };

    };

}
