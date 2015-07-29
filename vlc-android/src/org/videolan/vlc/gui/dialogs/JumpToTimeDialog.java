/*
 * *************************************************************************
 *  JumpToTimeDIalog.java
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

package org.videolan.vlc.gui.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.videolan.vlc.R;

public class JumpToTimeDialog extends PickTimeFragment {

    public JumpToTimeDialog(){
        super();
    }

    public static JumpToTimeDialog newInstance(int theme) {
        JumpToTimeDialog myFragment = new JumpToTimeDialog();

        Bundle args = new Bundle();
        args.putInt("theme", theme);
        myFragment.setArguments(args);

        return myFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, getArguments().getInt("theme"));
    }

    protected void executeAction() {
        if (mService == null)
            return;
        long hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        long minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        long seconds = !mSeconds.equals("") ? Long.parseLong(mSeconds) * SECONDS_IN_MICROS : 0l;
        mService.setTime((hours +  minutes + seconds)/1000l); //Time in ms
        dismiss();
    }

    @Override
    protected int getTitle() {
        return R.string.jump_to_time;
    }

    protected int getIcon() {
        return R.attr.ic_jumpto_normal_style;
    }
}
