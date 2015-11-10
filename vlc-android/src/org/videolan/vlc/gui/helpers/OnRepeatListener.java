/*
 * *************************************************************************
 *  OnRepeatListener.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;

import org.videolan.vlc.util.WeakHandler;

public class OnRepeatListener implements View.OnTouchListener {

    private static final int ACTION_ONCLICK = 0;

    //Default values in milliseconds
    private static final int DEFAULT_INITIAL_DELAY = 1000;
    private static final int DEFAULT_NORMAL_DELAY = 300;

    private int mInitialInterval;
    private final int mNormalInterval;
    private final View.OnClickListener mClickListener;
    private View downView;

    /**
     *
     * @param initialInterval Initial interval in millis
     * @param normalInterval Normal interval in millis
     * @param clickListener The OnClickListener to trigger
     */
    public OnRepeatListener(int initialInterval, int normalInterval,
                            View.OnClickListener clickListener) {
        if (clickListener == null)
            throw new IllegalArgumentException("null runnable");
        if (initialInterval < 0 || normalInterval < 0)
            throw new IllegalArgumentException("negative interval");

        this.mInitialInterval = initialInterval;
        this.mNormalInterval = normalInterval;
        this.mClickListener = clickListener;
    }

    /**
     *
     * @param clickListener The OnClickListener to trigger
     */
    public OnRepeatListener(View.OnClickListener clickListener) {
        this(DEFAULT_INITIAL_DELAY, DEFAULT_NORMAL_DELAY, clickListener);
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.removeMessages(ACTION_ONCLICK);
                mHandler.sendEmptyMessageDelayed(ACTION_ONCLICK, mInitialInterval);
                downView = view;
                mClickListener.onClick(view);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeMessages(ACTION_ONCLICK);
                downView = null;
                return true;
        }
        return false;
    }

    private Handler mHandler = new OnRepeatHandler(this);

    private static class OnRepeatHandler extends WeakHandler<OnRepeatListener> {

        public OnRepeatHandler(OnRepeatListener owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case ACTION_ONCLICK:
                    sendEmptyMessageDelayed(ACTION_ONCLICK, getOwner().mNormalInterval);
                    getOwner().mClickListener.onClick(getOwner().downView);
            }
        }
    }
}
