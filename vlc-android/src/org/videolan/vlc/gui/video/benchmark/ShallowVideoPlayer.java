/*****************************************************************************
 * ShallowVideoPlayer.java
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

package org.videolan.vlc.gui.video.benchmark;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.videolan.vlc.gui.video.VideoPlayerActivity;


/**
 * Class to store the overriden methods in BenchActivity
 * for code readability
 */
public class ShallowVideoPlayer extends VideoPlayerActivity {
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void onAudioSubClick(View anchor) {
    }

    @Override
    public void onClick(View v) {
    }
}
