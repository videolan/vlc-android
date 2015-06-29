/*
 * *************************************************************************
 *  AudioDelayDialog.java
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;

public class AudioDelayDialog extends PickTimeFragment implements PlaybackService.Client.Callback {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        view.findViewById(R.id.jump_millis_container).setVisibility(View.VISIBLE);
        view.findViewById(R.id.jump_millis_text).setVisibility(View.VISIBLE);
        view.findViewById(R.id.jump_hours_text).setVisibility(View.GONE);
        view.findViewById(R.id.jump_hours_container).setVisibility(View.GONE);

        mMillis.setOnFocusChangeListener(this);
        mMillis.setOnEditorActionListener(this);

        mMinutes.setNextFocusLeftId(R.id.jump_go);
        mActionButton.setNextFocusLeftId(R.id.jump_millis);
        mSign.setNextFocusRightId(R.id.jump_minutes);

        mSign.setVisibility(View.VISIBLE);

        mActionButton.setText(android.R.string.cancel);

        return view;
    }

    @Override
    protected void executeAction(){
        mService.setAudioDelay(getTime());
    }

    @Override
    protected void buttonAction() {
        initTime(0l);
        executeAction();
    }

    @Override
    protected long getMax() {
        return -1;
    }

    @Override
    protected long getInitTime() {
        return mService.getAudioDelay();
    }

    @Override
    protected int getTitle() {
        return R.string.audio_delay;
    }
}
