/*
 * *************************************************************************
 *  JumpToTimeDIalog.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.R;

public class JumpToTimeDialog extends PickTimeFragment {

    public JumpToTimeDialog(){
        super();
        mLiveAction = false;
        max = mLibVLC.getLength() * 1000l;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (mLibVLC.getLength() > HOURS_IN_MICROS) {
            mHours.setOnFocusChangeListener(this);
            mHours.setOnEditorActionListener(this);
            view.findViewById(R.id.jump_hours_up).setOnClickListener(this);
            view.findViewById(R.id.jump_hours_down).setOnClickListener(this);
        } else {
            view.findViewById(R.id.jump_hours_text).setVisibility(View.GONE);
            view.findViewById(R.id.jump_hours_container).setVisibility(View.GONE);
        }
        mMinutes.setNextFocusLeftId(R.id.jump_go);
        mSeconds.setNextFocusRightId(R.id.jump_go);
        initTime(mLibVLC.getTime()*1000);
        return view;
    }

    protected void executeAction() {
        long hours = mHours != null ? Long.parseLong(mHours.getText().toString()) * HOURS_IN_MICROS : 0l;
        long minutes = Long.parseLong(mMinutes.getText().toString()) * MINUTES_IN_MICROS ;
        long seconds = Long.parseLong(mSeconds.getText().toString()) * SECONDS_IN_MICROS;
        LibVLC.getExistingInstance().setTime((hours +  minutes + seconds)/1000l); //Time in ms
        dismiss();
    }

    @Override
    protected void buttonAction() {
        executeAction();
    }

    @Override
    protected int getTitle() {
        return R.string.jump_to_time;
    }
}
