/*****************************************************************************
 * AudioMiniPlayer.java
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

package org.videolan.vlc.widget;

import android.app.Activity;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioPlayerFragment;
import org.videolan.vlc.interfaces.IAudioPlayer;
import org.videolan.vlc.interfaces.IAudioPlayerControl;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class AudioMiniPlayer extends Fragment implements IAudioPlayer {
    public static final String TAG = "VLC/AudioMiniPlayer";

    private IAudioPlayerControl mAudioPlayerControl;
    private String lastTitle;

    private TextView mTitle;
    private TextView mArtist;
    private ImageButton mPlayPause;
    private ImageButton mForward;
    private ImageButton mBackward;
    private ImageView mCover;
    private ProgressBar mProgressBar;

    private float mTouchX, mTouchY;

    // Listener for the play and pause buttons
    private final OnClickListener onMediaControlClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAudioPlayerControl != null) {
                if (v == mPlayPause) {
                    if (mAudioPlayerControl.isPlaying()) {
                        mAudioPlayerControl.pause();
                    } else {
                        mAudioPlayerControl.play();
                    }
                } else if (v == mForward) {
                    mAudioPlayerControl.next();
                } else if (v == mBackward) {
                    mAudioPlayerControl.previous();
                }
            }
            update();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lastTitle = "";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.audio_player_mini, container, false);

        // Initialize the children
        mCover = (ImageView) v.findViewById(R.id.cover);
        mTitle = (TextView) v.findViewById(R.id.title);
        mArtist = (TextView) v.findViewById(R.id.artist);
        mPlayPause = (ImageButton) v.findViewById(R.id.play_pause);
        mForward = (ImageButton) v.findViewById(R.id.forward);
        mBackward = (ImageButton) v.findViewById(R.id.backward);
        mPlayPause.setOnClickListener(onMediaControlClickListener);
        mForward.setOnClickListener(onMediaControlClickListener);
        mBackward.setOnClickListener(onMediaControlClickListener);
        mProgressBar = (ProgressBar) v.findViewById(R.id.timeline);

        final LinearLayout root = (LinearLayout) v.findViewById(R.id.root_node);

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchX = event.getRawX();
                    mTouchY = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    if(mTouchY - event.getRawY() > root.getHeight()) {
                        // should show playlist view of AudioPlayerFragment
                        Toast.makeText(AudioMiniPlayer.this.getActivity(), "AudioMiniPlayer swipe up", Toast.LENGTH_SHORT).show();
                        return true;
                    } else if(Math.abs( mTouchY - event.getRawY() ) < 5 && Math.abs( mTouchX - event.getRawX() ) < 5) {
                        // effectively a click
                        // should show cover view of AudioPlayerFragment
                        AudioPlayerFragment.start(getActivity());
                        return true;
                    } else
                        return false;
                }
                return true;
            }
        });

        registerForContextMenu(v);
        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // The player should not be visible in the first place
        hide();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_player_mini, menu);

        MenuItem pp = menu.findItem(R.id.play_pause);
        if (mAudioPlayerControl.isPlaying()) {
            pp.setTitle(R.string.pause);
        } else {
            pp.setTitle(R.string.play);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.play_pause:
                if (mAudioPlayerControl.isPlaying())
                    mAudioPlayerControl.pause();
                else
                    mAudioPlayerControl.play();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    public void setAudioPlayerControl(IAudioPlayerControl control) {
        mAudioPlayerControl = control;
    }

    @Override
    public synchronized void update() {
        if (mAudioPlayerControl != null && getActivity() != null) {

            if (mAudioPlayerControl.hasMedia() && !mKeepHidden) {
                show();
            } else {
                hide();
                return;
            }

            String title = mAudioPlayerControl.getTitle();
            if (title != null && !title.equals(lastTitle)) {
                Bitmap cover = mAudioPlayerControl.getCover();
                if (cover != null) {
                    mCover.setVisibility(ImageView.VISIBLE);
                    mCover.setImageBitmap(cover);
                } else {
                    mCover.setVisibility(ImageView.GONE);
                }
            }

            lastTitle = title;
            mTitle.setText(lastTitle);
            mArtist.setText(mAudioPlayerControl.getArtist());
            if (mAudioPlayerControl.isPlaying()) {
                mPlayPause.setImageResource(R.drawable.ic_pause);
            } else {
                mPlayPause.setImageResource(R.drawable.ic_play);
            }
            if (mAudioPlayerControl.hasNext())
                mForward.setVisibility(ImageButton.VISIBLE);
            else
                mForward.setVisibility(ImageButton.INVISIBLE);
            if (mAudioPlayerControl.hasPrevious())
                mBackward.setVisibility(ImageButton.VISIBLE);
            else
                mBackward.setVisibility(ImageButton.INVISIBLE);
            int time = mAudioPlayerControl.getTime();
            int length = mAudioPlayerControl.getLength();
            // Update all view elements

            mProgressBar.setMax(length);
            mProgressBar.setProgress(time);
        }

    }

    public void show() {
        FragmentTransaction ft = getActivity().getSupportFragmentManager()
                .beginTransaction();
        ft.setCustomAnimations(R.anim.anim_enter_bottom, 0);
        ft.show(this);
        ft.commit();
    }

    public void hide() {
        FragmentTransaction ft = getActivity().getSupportFragmentManager()
                .beginTransaction();
        /*
            The exit animation won't run because of a bug in the compatibility library.
            See: https://code.google.com/p/android/issues/detail?id=32405
        */
        ft.setCustomAnimations(0, R.anim.anim_leave_bottom);
        ft.hide(this);
        ft.commit();
    }

    private boolean mKeepHidden = false;

    /**
     * Tell the mini player to keep hidden or not.
     * @param k true if the player must keep hidden, else false.
     */
    public void setKeepHidden(boolean k) {
        mKeepHidden = k;
        update();
    }
}
