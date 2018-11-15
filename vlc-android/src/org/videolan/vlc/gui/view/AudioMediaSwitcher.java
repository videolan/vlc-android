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

package org.videolan.vlc.gui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.util.WorkersKt;


public abstract class AudioMediaSwitcher extends FlingViewGroup {

    private AudioMediaSwitcherListener mAudioMediaSwitcherListener;

    private boolean hasPrevious;
    private int previousPosition;

    public AudioMediaSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnViewSwitchedListener(mViewSwitchListener);
    }

    public void updateMedia(final PlaybackService service) {
        if (service == null) return;
        final String artMrl = service.getCoverArt();
        final String prevArtMrl = service.getPrevCoverArt();
        final String nextArtMrl = service.getNextCoverArt();
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final Bitmap coverCurrent = artMrl != null ? AudioUtil.readCoverBitmap(Uri.decode(artMrl), 512) : null;
                final Bitmap coverPrev = prevArtMrl != null ? AudioUtil.readCoverBitmap(Uri.decode(prevArtMrl), 512) : null;
                final Bitmap coverNext = nextArtMrl != null ? AudioUtil.readCoverBitmap(Uri.decode(nextArtMrl), 512) : null;
                WorkersKt.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        removeAllViews();

                        hasPrevious = false;
                        previousPosition = 0;

                        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        if (service.hasPrevious()) {
                            addMediaView(inflater,
                                    service.getTitlePrev(),
                                    service.getArtistPrev(),
                                    coverPrev);
                            hasPrevious = true;
                        }
                        if (service.hasMedia())
                            addMediaView(inflater,
                                    service.getTitle(),
                                    service.getArtist(),
                                    coverCurrent);
                        if (service.hasNext())
                            addMediaView(inflater,
                                    service.getTitleNext(),
                                    service.getArtistNext(),
                                    coverNext);

                        if (service.hasPrevious() && service.hasMedia()) {
                            previousPosition = 1;
                            scrollTo(1);
                        }
                        else
                            scrollTo(0);
                    }
                });
            }
        });
    }

    protected abstract void addMediaView(LayoutInflater inflater, String title, String artist, Bitmap cover);

    private final ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {

        @Override
        public void onSwitching(float progress) {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener.onMediaSwitching();
        }

        @Override
        public void onSwitched(int position) {
            if (mAudioMediaSwitcherListener != null)
            {
                if (previousPosition != position) {
                    if (position == 0 && hasPrevious)
                        mAudioMediaSwitcherListener.onMediaSwitched(AudioMediaSwitcherListener.PREVIOUS_MEDIA);
                    if (position == 1 && !hasPrevious)
                        mAudioMediaSwitcherListener.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA);
                    else if (position == 2)
                        mAudioMediaSwitcherListener.onMediaSwitched(AudioMediaSwitcherListener.NEXT_MEDIA);
                    previousPosition = position;
                }
                else
                    mAudioMediaSwitcherListener.onMediaSwitched(AudioMediaSwitcherListener.CURRENT_MEDIA);
            }
        }

        @Override
        public void onTouchDown() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener.onTouchDown();
        }

        @Override
        public void onTouchUp() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener.onTouchUp();
        }

        @Override
        public void onTouchClick() {
            if (mAudioMediaSwitcherListener != null)
                mAudioMediaSwitcherListener.onTouchClick();
        }

        @Override
        public void onBackSwitched() {}
    };

    public void setAudioMediaSwitcherListener(AudioMediaSwitcherListener l) {
        mAudioMediaSwitcherListener = l;
    }

    public interface AudioMediaSwitcherListener {
        int PREVIOUS_MEDIA = 1;
        int CURRENT_MEDIA = 2;
        int NEXT_MEDIA = 3;

        void onMediaSwitching();

        void onMediaSwitched(int position);

        void onTouchDown();

        void onTouchUp();

        void onTouchClick();
    }
}
