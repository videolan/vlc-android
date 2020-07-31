package org.videolan.libvlc.interfaces;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import java.io.FileDescriptor;

public interface IMediaFactory extends IComponentFactory {
    String factoryId = IMediaFactory.class.getName();

    IMedia getFromLocalPath(ILibVLC ILibVLC, String path);
    IMedia getFromUri(ILibVLC ILibVLC, Uri uri);
    IMedia getFromFileDescriptor(ILibVLC ILibVLC, FileDescriptor fd);
    IMedia getFromAssetFileDescriptor(ILibVLC ILibVLC, AssetFileDescriptor assetFileDescriptor);
}
