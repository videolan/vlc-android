package org.videolan.libvlc.stubs;

import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.AbstractVLCEvent;
import org.videolan.libvlc.interfaces.IVLCObject;

public class StubVLCObject<T extends AbstractVLCEvent> implements IVLCObject<T> {
    @Override
    public boolean retain() {
        return false;
    }

    @Override
    public void release() {

    }

    @Override
    public boolean isReleased() {
        return false;
    }

    @Override
    public ILibVLC getLibVLC() {
        return null;
    }
}
