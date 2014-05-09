/*****************************************************************************
 * AudioPlaylistItemViewGroup.java
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

import android.content.Context;
import android.util.AttributeSet;


public class AudioPlaylistItemViewGroup extends FlingViewGroup {

    private OnItemSlidedListener mOnItemSlidedListener;

    public AudioPlaylistItemViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnViewSwitchedListener(mViewSwitchListener);
    }

    private final ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {

        @Override
        public void onSwitching(float progress) { }

        @Override
        public void onSwitched(int position) {
            if (mOnItemSlidedListener != null
                && position != 1)
                mOnItemSlidedListener.onItemSlided();
        }

        @Override
        public void onTouchDown() { }

        @Override
        public void onTouchUp() { }

        @Override
        public void onTouchClick() { }

        @Override
        public void onBackSwitched() {}

    };

    public void setOnItemSlidedListener(OnItemSlidedListener l) {
        mOnItemSlidedListener = l;
    }

    public interface OnItemSlidedListener {
        void onItemSlided();
    }
}
