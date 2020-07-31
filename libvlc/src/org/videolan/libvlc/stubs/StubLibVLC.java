package org.videolan.libvlc.stubs;

import android.content.Context;

import org.videolan.libvlc.interfaces.ILibVLC;

import java.util.List;

public class StubLibVLC extends StubVLCObject<ILibVLC.Event> implements ILibVLC {
    private final Context mContext;

    public StubLibVLC(Context context, List<String> options) {
        this.mContext = context;
    }

    public StubLibVLC(Context context) {
        this(context, null);
    }

    @Override
    public Context getAppContext() {
        return mContext;
    }
}
