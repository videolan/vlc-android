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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.TrackInfo;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.WeakHandler;

import com.actionbarsherlock.app.SherlockListFragment;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaInfoFragment extends SherlockListFragment {

    public final static String TAG = "VLC/MediaInfoFragment";
    private Media mItem;
    private Bitmap mImage;
    private TextView mTitleView;
    private TextView mLengthView;
    private ImageButton mPlayButton;
    private TrackInfo[] mTracks;
    private MediaInfoAdapter mAdapter;
    private final static int NEW_IMAGE = 0;
    private final static int NEW_TEXT = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = inflater.inflate(R.layout.media_info, container, false);

        mTitleView = (TextView) v.findViewById(R.id.title);
        mLengthView = (TextView) v.findViewById(R.id.length);
        mPlayButton = (ImageButton) v.findViewById(R.id.play);

        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoPlayerActivity.start(getActivity(), mItem.getLocation());
            }
        });

        mAdapter = new MediaInfoAdapter(getActivity());
        setListAdapter(mAdapter);

        update();

        return v;
    }

    public void setMediaLocation(String MRL) {
        if (MRL == null)
            return;
        mItem = MediaLibrary.getInstance(getActivity()).getMediaItem(MRL);
    }

    private void update() {
        if (mItem == null) {
            // Shouldn't happen, maybe user opened it faster than Media Library could index it
            return;
        }

        mTitleView.setText(mItem.getTitle());
        getSherlockActivity().getSupportActionBar().setTitle(mItem.getTitle());
        mLengthView.setText(Util.millisToString(mItem.getLength()));

        new Thread(mLoadImage).start();
    }

    Runnable mLoadImage = new Runnable() {
        @Override
        public void run() {
            LibVLC mLibVlc = null;
            try {
                mLibVlc = Util.getLibVlcInstance();
            } catch (LibVlcException e) {
                return;
            }

            mTracks = mLibVlc.readTracksInfo(mItem.getLocation());
            mHandler.sendEmptyMessage(NEW_TEXT);

            DisplayMetrics screen = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(screen);
            int width = Math.min(screen.widthPixels, screen.heightPixels);
            int height = width * 9 / 16;

            // Get the thumbnail.
            mImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            byte[] b = mLibVlc.getThumbnail(mItem.getLocation(), width, height);

            if (b == null) // We were not able to create a thumbnail for this item.
                return;

            mImage.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            mImage = Util.cropBorders(mImage, width, height);

            mHandler.sendEmptyMessage(NEW_IMAGE);
        }
    };

    private void updateImage() {
        if (getView() == null)
            return;
        ImageView imageView = (ImageView) getView().findViewById(R.id.image);
        imageView.setImageBitmap(mImage);
        mPlayButton.setVisibility(View.VISIBLE);
    }

    private void updateText() {
        for (TrackInfo track : mTracks) {
            if (track.Type != TrackInfo.TYPE_META)
                mAdapter.add(track);
        }
    }

    private Handler mHandler = new MediaInfoHandler(this);

    private static class MediaInfoHandler extends WeakHandler<MediaInfoFragment> {
        public MediaInfoHandler(MediaInfoFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaInfoFragment activity = getOwner();
            if(activity == null) return;

            switch (msg.what) {
                case NEW_IMAGE:
                    activity.updateImage();
                    break;
                case NEW_TEXT:
                    activity.updateText();
                    break;
            }
        };

    };

}
