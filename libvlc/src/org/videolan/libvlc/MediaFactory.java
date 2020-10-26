package org.videolan.libvlc;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMediaFactory;

import java.io.FileDescriptor;

public class MediaFactory implements IMediaFactory {

    @Override
    public IMedia getFromLocalPath(ILibVLC ILibVLC, String path) {
        return new Media(ILibVLC, path);
    }

    @Override
    public IMedia getFromUri(ILibVLC ILibVLC, Uri uri) {
        return new Media(ILibVLC, uri);
    }

    @Override
    public IMedia getFromFileDescriptor(ILibVLC ILibVLC, FileDescriptor fd) {
        return new Media(ILibVLC, fd);
    }

    @Override
    public IMedia getFromAssetFileDescriptor(ILibVLC ILibVLC, AssetFileDescriptor assetFileDescriptor) {
        return new Media(ILibVLC, assetFileDescriptor);
    }
}
