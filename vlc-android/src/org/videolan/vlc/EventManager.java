/*****************************************************************************
 * EventManager.java
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

package org.videolan.vlc;

import java.util.ArrayList;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class EventManager {

    /*
     * Be sure to subscribe to events you need in the JNI too.
     */

    //public static final int MediaMetaChanged                = 0;
    //public static final int MediaSubItemAdded               = 1;
    //public static final int MediaDurationChanged            = 2;
    //public static final int MediaParsedChanged              = 3;
    //public static final int MediaFreed                      = 4;
    //public static final int MediaStateChanged               = 5;

    //public static final int MediaPlayerMediaChanged         = 0x100;
    //public static final int MediaPlayerNothingSpecial       = 0x101;
    //public static final int MediaPlayerOpening              = 0x102;
    //public static final int MediaPlayerBuffering            = 0x103;
    public static final int MediaPlayerPlaying                = 0x104;
    public static final int MediaPlayerPaused                 = 0x105;
    public static final int MediaPlayerStopped                = 0x106;
    //public static final int MediaPlayerForward              = 0x107;
    //public static final int MediaPlayerBackward             = 0x108;
    public static final int MediaPlayerEndReached             = 0x109;
    //public static final int MediaPlayerEncounteredError     = 0x10a;
    //public static final int MediaPlayerTimeChanged          = 0x10b;
    //public static final int MediaPlayerPositionChanged      = 0x10c;
    //public static final int MediaPlayerSeekableChanged      = 0x10d;
    //public static final int MediaPlayerPausableChanged      = 0x10e;
    //public static final int MediaPlayerTitleChanged         = 0x10f;
    //public static final int MediaPlayerSnapshotTaken        = 0x110;
    //public static final int MediaPlayerLengthChanged        = 0x111;
    public static final int MediaPlayerVout                   = 0x112;

    public static final int MediaListItemAdded                = 0x200;
    //public static final int MediaListWillAddItem            = 0x201;
    public static final int MediaListItemDeleted              = 0x202;
    //public static final int MediaListWillDeleteItem         = 0x203;

    //public static final int MediaListViewItemAdded          = 0x300;
    //public static final int MediaListViewWillAddItem        = 0x301;
    //public static final int MediaListViewItemDeleted        = 0x302;
    //public static final int MediaListViewWillDeleteItem     = 0x303;

    //public static final int MediaListPlayerPlayed           = 0x400;
    //public static final int MediaListPlayerNextItemSet      = 0x401;
    //public static final int MediaListPlayerStopped          = 0x402;

    //public static final int MediaDiscovererStarted          = 0x500;
    //public static final int MediaDiscovererEnded            = 0x501;

    //public static final int VlmMediaAdded                   = 0x600;
    //public static final int VlmMediaRemoved                 = 0x601;
    //public static final int VlmMediaChanged                 = 0x602;
    //public static final int VlmMediaInstanceStarted         = 0x603;
    //public static final int VlmMediaInstanceStopped         = 0x604;
    //public static final int VlmMediaInstanceStatusInit      = 0x605;
    //public static final int VlmMediaInstanceStatusOpening   = 0x606;
    //public static final int VlmMediaInstanceStatusPlaying   = 0x607;
    //public static final int VlmMediaInstanceStatusPause     = 0x608;
    //public static final int VlmMediaInstanceStatusEnd       = 0x609;
    //public static final int VlmMediaInstanceStatusError     = 0x60a;

    private ArrayList<Handler> mEventHandler;
    private static EventManager mInstance;

    private EventManager() {
        mEventHandler = new ArrayList<Handler>();
    }

    public static EventManager getInstance() {
        if (mInstance == null) {
            mInstance = new EventManager();
        }
        return mInstance;
    }

    public void addHandler(Handler handler) {
        if (!mEventHandler.contains(handler))
            mEventHandler.add(handler);
    }

    public void removeHandler(Handler handler) {
        mEventHandler.remove(handler);
    }

    /** This method is called by a native thread **/
    public void callback(int event, Bundle b) {
        b.putInt("event", event);
        for (int i = 0; i < mEventHandler.size(); i++) {
            Message msg = Message.obtain();
            msg.setData(b);
            mEventHandler.get(i).sendMessage(msg);
        }
    }
}
