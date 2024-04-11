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
        return ml.isInitiated() && nativeAddSubscription(ml, this.type.value, mrl);
    }

    @Override
    public boolean isAutoDownloadEnabled() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeIsAutoDownloadEnabled(ml, this.type.value);
    }

    @Override
    public boolean setAutoDownloadEnabled(boolean enabled) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetAutoDownloadEnabled(ml, this.type.value, enabled);
    }

    @Override
    public boolean isNewMediaNotificationEnabled() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeIsNewMediaNotificationEnabled(ml, this.type.value);
    }

    @Override
    public boolean setNewMediaNotificationEnabled(boolean enabled) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetNewMediaNotificationEnabled(ml, this.type.value, enabled);
    }

    @Override
    public long getMaxCacheSize() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetServiceMaxCacheSize(ml, this.type.value) : -2L;
    }

    @Override
    public boolean setMaxCacheSize(long size) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetServiceMaxCacheSize(ml, this.type.value, size);
    }

    @Override
    public int getNbSubscriptions() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetNbSubscriptions(ml, this.type.value) : -1;
    }

    @Override
    public int getNbUnplayedMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetNbUnplayedMedia(ml, this.type.value) : -1;
    }

    @Override
    public Subscription[] getSubscriptions(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetSubscriptions(ml, this.type.value, sort, desc, includeMissing, onlyFavorites) : new Subscription[0];
    }

    @Override
    public int getNbMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetNbMedia(ml, this.type.value) : -1;
    }

    @Override
    public MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() ? nativeGetServiceMedia(ml, this.type.value, sortingCriteria, desc, includeMissing, onlyFavorites) : Medialibrary.EMPTY_COLLECTION;
    }

    @Override
    public boolean refresh() {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeServiceRefresh(ml, this.type.value);
    }

    private native boolean nativeAddSubscription(Medialibrary ml, int type, String mrl);
    private native boolean nativeIsAutoDownloadEnabled(Medialibrary ml, int type);
    private native boolean nativeSetAutoDownloadEnabled(Medialibrary ml, int type, boolean enabled);
    private native boolean nativeIsNewMediaNotificationEnabled(Medialibrary ml, int type);
    private native boolean nativeSetNewMediaNotificationEnabled(Medialibrary ml, int type, boolean enabled);
    private native long nativeGetServiceMaxCacheSize(Medialibrary ml, int type);
    private native boolean nativeSetServiceMaxCacheSize(Medialibrary ml, int type, long size);
    private native int nativeGetNbSubscriptions(Medialibrary ml, int type);
    private native int nativeGetNbUnplayedMedia(Medialibrary ml, int type);
    private native Subscription[] nativeGetSubscriptions(Medialibrary ml, int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    private native int nativeGetNbMedia(Medialibrary ml, int type);
    private native MediaWrapper[] nativeGetServiceMedia(Medialibrary ml, int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    private native boolean nativeServiceRefresh(Medialibrary ml, int type);
}
