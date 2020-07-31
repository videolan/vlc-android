package org.videolan.libvlc.interfaces;

public interface IVLCObject<T extends AbstractVLCEvent> {
    boolean retain();

    void release();

    boolean isReleased();

    ILibVLC getLibVLC();
}
