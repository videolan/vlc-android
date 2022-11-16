package org.videolan.medialibrary.media;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.MlService;
import org.videolan.medialibrary.interfaces.media.Subscription;

public class MlServiceImpl extends MlService {
    MlServiceImpl(Type type) {super(type);}
    MlServiceImpl(int type) {super(type);}

    public boolean addSubscription(String mrl) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeAddSubscription(ml, this.type.value, mrl);
    }

    @Override
    public boolean isAutoDownloadEnabled() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeIsAutoDownloadEnabled(ml, this.type.value);
    }

    @Override
    public boolean setAutoDownloadEnabled(boolean enabled) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetAutoDownloadEnabled(ml, this.type.value, enabled);
    }

    @Override
    public boolean isNewMediaNotificationEnabled() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeIsNewMediaNotificationEnabled(ml, this.type.value);
    }

    @Override
    public boolean setNewMediaNotificationEnabled(boolean enabled) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetNewMediaNotificationEnabled(ml, this.type.value, enabled);
    }

    @Override
    public long getMaxCachedSize() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetMaxCachedSize(ml, this.type.value);
    }

    @Override
    public boolean setMaxCachedSize(long size) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetMaxCachedSize(ml, this.type.value, size);
    }

    @Override
    public int getNbSubscriptions() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetNbSubscriptions(ml, this.type.value);
    }

    @Override
    public int getNbUnplayedMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetNbUnplayedMedia(ml, this.type.value);
    }

    @Override
    public Subscription[] getSubscriptions(int sort, boolean desc, boolean includeMissing) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptions(ml, this.type.value, sort, desc, includeMissing);
    }

    @Override
    public int getNbMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetNbMedia(ml, this.type.value);
    }

    @Override
    public MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetServiceMedia(ml, this.type.value, sortingCriteria, desc, includeMissing);
    }

    private native boolean nativeAddSubscription(Medialibrary ml, int type, String mrl);
    private native boolean nativeIsAutoDownloadEnabled(Medialibrary ml, int type);
    private native boolean nativeSetAutoDownloadEnabled(Medialibrary ml, int type, boolean enabled);
    private native boolean nativeIsNewMediaNotificationEnabled(Medialibrary ml, int type);
    private native boolean nativeSetNewMediaNotificationEnabled(Medialibrary ml, int type, boolean enabled);
    private native long nativeGetMaxCachedSize(Medialibrary ml, int type);
    private native boolean nativeSetMaxCachedSize(Medialibrary ml, int type, long size);
    private native int nativeGetNbSubscriptions(Medialibrary ml, int type);
    private native int nativeGetNbUnplayedMedia(Medialibrary ml, int type);
    private native Subscription[] nativeGetSubscriptions(Medialibrary ml, int type, int sort, boolean desc, boolean includeMissing);
    private native int nativeGetNbMedia(Medialibrary ml, int type);
    private native MediaWrapper[] nativeGetServiceMedia(Medialibrary ml, int type, int sort, boolean desc, boolean includeMissing);
}
