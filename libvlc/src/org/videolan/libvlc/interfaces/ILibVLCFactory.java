package org.videolan.libvlc.interfaces;

import android.content.Context;

import java.util.List;

public interface ILibVLCFactory extends IComponentFactory {
    String factoryId = ILibVLCFactory.class.getName();

    ILibVLC getFromOptions(Context context, List<String> options);

    ILibVLC getFromContext(Context context);
}
