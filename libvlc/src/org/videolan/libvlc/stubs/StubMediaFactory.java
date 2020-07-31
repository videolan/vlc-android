package org.videolan.libvlc.stubs;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import org.videolan.libvlc.interfaces.IMediaFactory;
import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.IMedia;

import java.io.FileDescriptor;

public class StubMediaFactory implements IMediaFactory {
    @Override
    public IMedia getFromLocalPath(ILibVLC ILibVLC, String path) {
        return new StubMedia(ILibVLC, path);
    }

    @Override
    public IMedia getFromUri(ILibVLC ILibVLC, Uri uri) {
        return new StubMedia(ILibVLC, uri);
    }

    @Override
    public IMedia getFromFileDescriptor(ILibVLC ILibVLC, FileDescriptor fd) {
        return new StubMedia(ILibVLC, fd);
    }

    @Override
    public IMedia getFromAssetFileDescriptor(ILibVLC ILibVLC, AssetFileDescriptor assetFileDescriptor) {
        return new StubMedia(ILibVLC, assetFileDescriptor);
    }
}
