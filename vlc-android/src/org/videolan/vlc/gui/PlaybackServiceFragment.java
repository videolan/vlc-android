/*
 * *************************************************************************
 *  PlaybackServiceFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors, VideoLAN, and VideoLabs
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

package org.videolan.vlc.gui;

import android.app.Activity;
import android.support.v4.app.Fragment;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

public abstract class PlaybackServiceFragment extends Fragment implements PlaybackService.Client.Callback {
    protected PlaybackService mService;

    private static PlaybackServiceActivity.Helper getHelper(Activity activity) {
        if (activity == null)
            return null;

        if ((activity instanceof AudioPlayerContainerActivity))
            return ((AudioPlayerContainerActivity) activity).getHelper();
        else if ((activity instanceof PlaybackServiceActivity))
            return ((PlaybackServiceActivity) activity).getHelper();
        else if ((activity instanceof VideoPlayerActivity))
            return ((VideoPlayerActivity) activity).getHelper();
        else
            return null;
    }

    private static void registerPlaybackService(Activity activity, PlaybackService.Client.Callback cb) {
        final PlaybackServiceActivity.Helper helper = getHelper(activity);
        if (helper != null)
            helper.registerFragment(cb);
    }
    private static void unregisterPlaybackService(Activity activity, PlaybackService.Client.Callback cb) {
        final PlaybackServiceActivity.Helper helper = getHelper(activity);
        if (helper != null)
            helper.unregisterFragment(cb);
    }

    public static void registerPlaybackService(Fragment fragment, PlaybackService.Client.Callback cb) {
        registerPlaybackService(fragment.getActivity(), cb);
    }
    public static void registerPlaybackService(android.app.Fragment fragment, PlaybackService.Client.Callback cb) {
        registerPlaybackService(fragment.getActivity(), cb);

    }

    public static void unregisterPlaybackService(Fragment fragment, PlaybackService.Client.Callback cb) {
        unregisterPlaybackService(fragment.getActivity(), cb);
    }
    public static void unregisterPlaybackService(android.app.Fragment fragment, PlaybackService.Client.Callback cb) {
        unregisterPlaybackService(fragment.getActivity(), cb);
    }

    public void onStart(){
        super.onStart();
        registerPlaybackService(this, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterPlaybackService(this, this);
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
