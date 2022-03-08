package org.videolan.libvlc.stubs;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import org.videolan.libvlc.interfaces.ILibVLC;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMediaList;

import java.io.FileDescriptor;

public class StubMedia extends StubVLCObject<IMedia.Event> implements IMedia {
    private Uri mUri;
    private ILibVLC mILibVLC;

    private int mType = Type.Unknown;

    public StubMedia(ILibVLC ILibVLC, String path) {
        this(ILibVLC, Uri.parse(path));
    }

    public StubMedia(ILibVLC ILibVLC, Uri uri) {
        mUri = uri;
        mILibVLC = ILibVLC;
    }

    public StubMedia(ILibVLC ILibVLC, FileDescriptor fd) {
        mILibVLC = ILibVLC;
    }

    public StubMedia(ILibVLC ILibVLC, AssetFileDescriptor assetFileDescriptor) {
        mILibVLC = ILibVLC;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public IMediaList subItems() {
        return new StubMediaList();
    }

    @Override
    public boolean parse(int flags) {
        return false;
    }

    @Override
    public boolean parse() {
        return false;
    }

    @Override
    public boolean parseAsync(int flags, int timeout) {
        return false;
    }

    @Override
    public boolean parseAsync(int flags) {
        return false;
    }

    @Override
    public boolean parseAsync() {
        return false;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public int getTrackCount() {
        return 0;
    }

    @Override
    public Track getTrack(int idx) {
        return null;
    }

    @Override
    public String getMeta(int id) {
        if (mUri == null)
            return null;
        switch (id) {
            case Meta.Title:
                return getTitle();
            case Meta.URL:
                return mUri.getPath();
        }
        return null;
    }

    @Override
    public String getMeta(int id, boolean force) {
        return getMeta(id);
    }

    private String getTitle() {
        if ("file".equals(mUri.getScheme())) {
            return mUri.getLastPathSegment();
        }
        return mUri.getPath();
    }

    @Override
    public void setHWDecoderEnabled(boolean enabled, boolean force) {

    }

    @Override
    public void setEventListener(EventListener listener) {

    }

    @Override
    public void addOption(String option) {

    }

    @Override
    public void addSlave(Slave slave) {

    }

    @Override
    public void clearSlaves() {

    }

    @Override
    public Slave[] getSlaves() {
        return new Slave[0];
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public boolean isParsed() {
        return false;
    }

    @Override
    public Stats getStats() {
        return null;
    }

    @Override
    public void setDefaultMediaPlayerOptions() {

    }

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
        return mILibVLC;
    }

    public void setType(int type) {
        this.mType = type;
    }
}
