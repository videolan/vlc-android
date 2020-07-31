package org.videolan.libvlc.interfaces;

import android.content.Context;

public interface ILibVLC extends IVLCObject<ILibVLC.Event> {
    class Event extends AbstractVLCEvent {
        protected Event(int type) {
            super(type);
        }
    }

    Context getAppContext();
}
