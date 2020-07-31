package org.videolan.libvlc.stubs;

import android.content.Context;

import org.videolan.libvlc.interfaces.ILibVLCFactory;
import org.videolan.libvlc.interfaces.ILibVLC;

import java.util.List;

public class StubLibVLCFactory implements ILibVLCFactory {
    @Override
    public ILibVLC getFromOptions(Context context, List<String> options) {
        return new StubLibVLC(context, options);
    }

    @Override
    public ILibVLC getFromContext(Context context) {
        return new StubLibVLC(context);
    }
}
