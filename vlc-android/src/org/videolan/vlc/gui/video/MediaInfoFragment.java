/*****************************************************************************
 * MediaInfoActivity.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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

import java.io.File;
import java.nio.ByteBuffer;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.TrackInfo;
import org.videolan.libvlc.util.Extensions;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.util.BitmapUtil;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaInfoFragment extends ListFragment {

    public final static String TAG = "VLC/MediaInfoFragment";
    LibVLC mLibVlc = null;

    private MediaWrapper mItem;
    private Bitmap mImage;
    private TextView mLengthView;
    private TextView mSizeView;
    private TextView mPathView;
    private ImageButton mPlayButton;
    private TextView mDelete;
    private ImageView mSubtitles;
    private TrackInfo[] mTracks;
    private MediaInfoAdapter mAdapter;
    private final static int NEW_IMAGE = 0;
    private final static int NEW_TEXT = 1;
    private final static int NEW_SIZE = 2;
    private final static int HIDE_DELETE = 3;
    private final static int EXIT = 4;
    private final static int SHOW_SUBTITLES = 5;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = inflater.inflate(R.layout.media_info, container, false);

        mLengthView = (TextView) v.findViewById(R.id.length);
        mSizeView = (TextView) v.findViewById(R.id.size_value);
        mPathView = (TextView) v.findViewById(R.id.info_path);
        mPlayButton = (ImageButton) v.findViewById(R.id.play);
        mDelete = (TextView) v.findViewById(R.id.info_delete);
        mSubtitles = (ImageView) v.findViewById(R.id.info_subtitles);
        if (!LibVlcUtil.isICSOrLater())
            mDelete.setText(getString(R.string.delete).toUpperCase());

        mPathView.setText(Uri.decode(mItem.getLocation().substring(7)));
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoPlayerActivity.start(getActivity(), mItem.getLocation());
            }
        });

        mDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mItem != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean deleted = Util.deleteFile(getActivity(), mItem.getLocation());
                            if (deleted) {
                                mHandler.obtainMessage(EXIT).sendToTarget();
                            }
                        }
                    }).start();
                }
            }
        });
        mAdapter = new MediaInfoAdapter(getActivity());
        setListAdapter(mAdapter);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mItem == null) {
            // Shouldn't happen, maybe user opened it faster than Media Library could index it
            return;
        }

        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(mItem.getTitle());
        mLengthView.setText(Strings.millisToString(mItem.getLength()));

        new Thread(mLoadImage).start();
        new Thread(mCheckFile).start();
    }

    public void setMediaLocation(String MRL) {
        if (MRL == null)
            return;
        mItem = MediaLibrary.getInstance().getMediaItem(MRL);
    }

    Runnable mCheckFile = new Runnable() {
        @Override
        public void run() {
            File itemFile = new File(Uri.decode(mItem.getLocation().substring(5)));
            if (!itemFile.canWrite())
                mHandler.obtainMessage(HIDE_DELETE).sendToTarget();
            long length = itemFile.length();
            mHandler.obtainMessage(NEW_SIZE, Long.valueOf(length)).sendToTarget();
            checkSubtitles(itemFile);
        }
    };

    private void checkSubtitles(File itemFile) {
        String extension, filename, videoName = Uri.decode(itemFile.getName()), parentPath = Uri.decode(itemFile.getParent());
        videoName = videoName.substring(0, videoName.lastIndexOf('.'));
        String[] subFolders = {"/Subtitles", "/subtitles", "/Subs", "/subs"};
        String[] files = itemFile.getParentFile().list();
        for (int i = 0 ; i < subFolders.length ; ++i){
            File subFolder = new File(parentPath+subFolders[i]);
            if (!subFolder.exists())
                continue;
            String[] subFiles = subFolder.list();
            String[] newFiles = new String[files.length+subFiles.length];
            System.arraycopy(subFiles, 0, newFiles, 0, subFiles.length);
            System.arraycopy(files, 0, newFiles, subFiles.length, files.length);
            files = newFiles;
        }
        for (int i = 0; i<files.length ; ++i){
            filename = Uri.decode(files[i]);
            extension = filename.substring(filename.lastIndexOf('.')+1);
            if (!Extensions.SUBTITLES.contains(extension))
                continue;
            if (filename.startsWith(videoName)) {
                mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
                return;
            }
        }
    }

    Runnable mLoadImage = new Runnable() {
        @Override
        public void run() {
            try {
                mLibVlc = VLCInstance.getLibVlcInstance();
            } catch (LibVlcException e) {
                return;
            }
            mTracks = mLibVlc.readTracksInfo(mItem.getLocation());
            int videoHeight = mItem.getHeight();
            int videoWidth = mItem.getWidth();
            if (videoWidth == 0 || videoHeight == 0)
                return;

            mHandler.sendEmptyMessage(NEW_TEXT);

            DisplayMetrics screen = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(screen);
            int width, height;
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                width = Math.min(screen.widthPixels, screen.heightPixels);
            } else {
                width = screen.widthPixels /2 ;
            }
            height = width * videoHeight/videoWidth;

            // Get the thumbnail.
            mImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            byte[] b = mLibVlc.getThumbnail(mItem.getLocation(), width, height);

            if (b == null) // We were not able to create a thumbnail for this item.
                return;

            mImage.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            mImage = BitmapUtil.cropBorders(mImage, width, height);

            mHandler.sendEmptyMessage(NEW_IMAGE);
        }
    };

    private void updateImage() {
        if (getView() == null)
            return;
        ImageView imageView = (ImageView) getView().findViewById(R.id.image);
        imageView.setImageBitmap(mImage);
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = mImage.getHeight();
        lp.width = mImage.getWidth();
        imageView.setLayoutParams(lp);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mPlayButton.setVisibility(View.VISIBLE);
        mLengthView.setVisibility(View.VISIBLE);
    }

    private void updateText() {
        boolean hasSubs = false;
        for (TrackInfo track : mTracks) {
            if (track.Type != TrackInfo.TYPE_META) {
                mAdapter.add(track);
                if (track.Type == TrackInfo.TYPE_TEXT)
                    hasSubs = true;
            }
        }
        if (mAdapter.isEmpty()) {
            ((MainActivity) getActivity()).popSecondaryFragment();
            return;
        }
        if (hasSubs)
            mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
    }

    private void updateSize(Long size){
        mSizeView.setText(Strings.readableFileSize(size.longValue()));
    }
    private Handler mHandler = new MediaInfoHandler(this);

    private static class MediaInfoHandler extends WeakHandler<MediaInfoFragment> {
        public MediaInfoHandler(MediaInfoFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaInfoFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case NEW_IMAGE:
                    fragment.updateImage();
                    break;
                case NEW_TEXT:
                    fragment.updateText();
                    break;
                case NEW_SIZE:
                    fragment.updateSize((Long) msg.obj);
                    break;
                case HIDE_DELETE:
                    fragment.mDelete.setClickable(false);
                    fragment.mDelete.setVisibility(View.GONE);
                    break;
                case EXIT:
                    ((MainActivity) fragment.getActivity()).popSecondaryFragment();
                    MediaLibrary.getInstance().loadMediaItems(fragment.getActivity(), true);
                    break;
                case SHOW_SUBTITLES:
                    fragment.mSubtitles.setVisibility(View.VISIBLE);
                    break;
            }
        };

    };

}
