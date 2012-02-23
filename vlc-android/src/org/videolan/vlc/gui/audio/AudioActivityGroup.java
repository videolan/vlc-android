/*****************************************************************************
 * AudioActivityGroup.java
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

package org.videolan.vlc.gui.audio;

import java.util.ArrayList;

import org.videolan.vlc.AudioServiceController;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.LocalActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

public class AudioActivityGroup extends ActivityGroup {
    public final static String TAG = "VLC/AudioActivityGroup";

    private ArrayList<String> mHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHistory = new ArrayList<String>();
        // Load VideoListActivity by default
        Intent intent = new Intent(this, AudioBrowserActivity.class);
        startChildAcitvity("AudioBrowserActivity", intent);
    }

    @Override
    protected void onResume() {
        AudioServiceController.getInstance().bindAudioService(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        AudioServiceController.getInstance().unbindAudioService(this);
        super.onPause();
    }

    public void startChildAcitvity(String id, Intent intent) {
        Window window = getLocalActivityManager().startActivity(
                id, intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        if (window != null) {
            mHistory.add(id);
            setContentView(window.getDecorView());
        }
    }

    @Override
    public void finishFromChild(Activity child) {
        LocalActivityManager manager = getLocalActivityManager();
        int index = mHistory.size() - 1;

        if (index > 0) {
            manager.destroyActivity(mHistory.get(index), true);
            mHistory.remove(index);
            index--;
            String id = mHistory.get(index);
            Activity activity = manager.getActivity(id);
            setContentView(activity.getWindow().getDecorView());
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        int index = mHistory.size() - 1;

        if (index > 0) {
            getCurrentActivity().finish();
            return;
        }

        super.onBackPressed();
    }
}
