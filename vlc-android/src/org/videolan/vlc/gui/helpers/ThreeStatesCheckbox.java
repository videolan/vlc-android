/*
 * *************************************************************************
 *  ThreeStatesCheckbox.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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

import android.content.Context;
import androidx.appcompat.widget.AppCompatCheckBox;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import org.videolan.vlc.R;

public class ThreeStatesCheckbox extends AppCompatCheckBox {

    public static final int STATE_UNCHECKED = 0;
    public static final int STATE_CHECKED = 1;
    public static final int STATE_PARTIAL = 2;
    private int mState = 0;

    public ThreeStatesCheckbox(Context context) {
        super(context);
        init();
    }

    public ThreeStatesCheckbox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThreeStatesCheckbox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        updateBtn();
        setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                switch (mState) {
                    case STATE_PARTIAL:
                    case STATE_UNCHECKED:
                        mState = STATE_CHECKED;
                        break;
                    case STATE_CHECKED:
                        mState = STATE_UNCHECKED;
                        break;
                }
                updateBtn();
            }
        });
    }

    private void updateBtn()
    {
        int btnDrawable;
        switch (mState) {
            case STATE_PARTIAL:
                btnDrawable = R.drawable.ic_checkbox_partialy;
                break;
            case STATE_CHECKED:
                btnDrawable = R.drawable.ic_checkbox_true;
                break;
            default:
                btnDrawable = R.drawable.ic_checkbox_false;
        }
        setButtonDrawable(btnDrawable);

    }
    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
        updateBtn();
    }
}
