/*****************************************************************************
 * PlayerControlClassic.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

import org.videolan.vlc.R;
import org.videolan.vlc.interfaces.IPlayerControl;
import org.videolan.vlc.interfaces.OnPlayerControlListener;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class PlayerControlClassic extends LinearLayout implements IPlayerControl {

    private ImageButton mBackward;
    private ImageButton mPlayPause;
    private ImageButton mForward;
    private OnPlayerControlListener listener = null;

    public PlayerControlClassic(Context context) {
        super(context);

        LayoutInflater.from(context).inflate(R.layout.player_contol_classic, this, true);

        mBackward = (ImageButton) findViewById(R.id.player_overlay_backward);
        mBackward.setOnClickListener(mBackwardListener);
        mPlayPause = (ImageButton) findViewById(R.id.player_overlay_play);
        mPlayPause.setOnClickListener(mPlayPauseListener);
        mForward = (ImageButton) findViewById(R.id.player_overlay_forward);
        mForward.setOnClickListener(mForwardListener);
    }

    private OnClickListener mBackwardListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (listener != null)
                listener.onSeek(-10000);
        }
    };
    private OnClickListener mPlayPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (listener != null)
                listener.onPlayPause();
        }
    };
    private OnClickListener mForwardListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (listener != null)
                listener.onSeek(10000);
        }
    };

    @Override
    public void setState(boolean isPlaying) {
        if (isPlaying) {
            mPlayPause.setBackgroundResource(R.drawable.ic_pause);
        } else {
            mPlayPause.setBackgroundResource(R.drawable.ic_play);
        }
    }

    @Override
    public void setOnPlayerControlListener(OnPlayerControlListener listener) {
        this.listener = listener;
    }
}
